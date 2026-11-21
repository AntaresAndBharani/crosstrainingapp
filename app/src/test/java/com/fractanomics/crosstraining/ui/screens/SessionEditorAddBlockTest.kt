package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.BlockInsert
import com.fractanomics.crosstraining.data.FakeSampleAppDatabase
import com.fractanomics.crosstraining.data.FakeTransactionRunner
import com.fractanomics.crosstraining.data.Repository
import com.fractanomics.crosstraining.data.model.BlockKind
import com.fractanomics.crosstraining.data.model.BlockSet
import com.fractanomics.crosstraining.data.model.BlockWithSets
import com.fractanomics.crosstraining.data.model.Cycle
import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.ExerciseCategory
import com.fractanomics.crosstraining.data.model.MetricType
import com.fractanomics.crosstraining.data.model.RepMax
import com.fractanomics.crosstraining.data.model.Session
import com.fractanomics.crosstraining.data.model.SessionBlock
import com.fractanomics.crosstraining.data.model.SessionWithBlocks
import com.fractanomics.crosstraining.util.AvailableBlock
import com.fractanomics.crosstraining.util.AvailableBlockCodec
import com.fractanomics.crosstraining.util.AvailableBlockExercise
import com.fractanomics.crosstraining.util.TrackedPrSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class SessionEditorAddBlockTest {

    private lateinit var fakeDb: FakeSampleAppDatabase
    private lateinit var repository: Repository

    private val rdl = Exercise(
        id = 1L,
        name = "Romanian Deadlift",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val frontSquat = Exercise(
        id = 2L,
        name = "Front Squat",
        category = ExerciseCategory.BARBELL,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )
    private val walkingLunge = Exercise(
        id = 3L,
        name = "Walking Lunge",
        category = ExerciseCategory.ACCESSORY,
        metricType = MetricType.WEIGHT,
        unit = "kg",
        tracksRepMax = true
    )

    @Before
    fun setup() {
        fakeDb = FakeSampleAppDatabase()
        repository = Repository(
            db = fakeDb,
            transactionRunner = FakeTransactionRunner(fakeDb)
        )
    }

    @Test
    fun `resolveInitialAddBlockTab respects SMART_DEFAULT and PREFER_SAVED routing rules`() {
        val blockA = AvailableBlock(
            id = 10L,
            title = "Block A",
            instructions = "",
            exercises = emptyList(),
            trackedPrs = emptyList()
        )
        val blockB = AvailableBlock(
            id = 11L,
            title = "Block B",
            instructions = "",
            exercises = emptyList(),
            trackedPrs = emptyList()
        )

        assertEquals(
            AddBlockSheetTab.SAVED_BLOCKS,
            resolveInitialAddBlockTab(
                preferSaved = false,
                availableBlocks = listOf(blockA, blockB),
                alreadyLoadedIds = setOf(10L)
            )
        )
        assertEquals(
            AddBlockSheetTab.CUSTOM_BLOCK,
            resolveInitialAddBlockTab(
                preferSaved = false,
                availableBlocks = listOf(blockA),
                alreadyLoadedIds = setOf(10L)
            )
        )
        assertEquals(
            AddBlockSheetTab.CUSTOM_BLOCK,
            resolveInitialAddBlockTab(
                preferSaved = false,
                availableBlocks = emptyList(),
                alreadyLoadedIds = emptySet()
            )
        )

        assertEquals(
            AddBlockSheetTab.SAVED_BLOCKS,
            resolveInitialAddBlockTab(
                preferSaved = true,
                availableBlocks = listOf(blockA),
                alreadyLoadedIds = setOf(10L)
            )
        )
        assertEquals(
            AddBlockSheetTab.CUSTOM_BLOCK,
            resolveInitialAddBlockTab(
                preferSaved = true,
                availableBlocks = emptyList(),
                alreadyLoadedIds = emptySet()
            )
        )
    }

    @Test
    fun `formatSavedBlocksConfirmLabel formats zero, singular, and plural selection counts`() {
        assertEquals("Select Complex(es)", formatSavedBlocksConfirmLabel(0))
        assertEquals("Add 1 Complex", formatSavedBlocksConfirmLabel(1))
        assertEquals("Add 3 Complexes", formatSavedBlocksConfirmLabel(3))
    }

    @Test
    fun `resolveCustomBlockTitle and canSubmitCustomBlock handle explicit title, chips, and zero selections`() {
        assertEquals(
            "Lower Strength",
            resolveCustomBlockTitle("  Lower Strength  ", listOf(rdl, frontSquat))
        )
        assertEquals(
            "Romanian Deadlift",
            resolveCustomBlockTitle("", listOf(rdl))
        )
        assertEquals(
            "Romanian Deadlift + Front Squat",
            resolveCustomBlockTitle("   ", listOf(rdl, frontSquat))
        )
        assertEquals(
            "Romanian Deadlift + Front Squat +1 more",
            resolveCustomBlockTitle("", listOf(rdl, frontSquat, walkingLunge))
        )
        assertEquals("", resolveCustomBlockTitle("   ", emptyList()))

        assertFalse(canSubmitCustomBlock("   ", emptyList()))
        assertTrue(canSubmitCustomBlock("Push Press", emptyList()))
        assertTrue(canSubmitCustomBlock("", listOf(1L)))
    }

    @Test
    fun `canRemoveResidualLegacyBlock allows deleting last legacy block when simplified cards exist`() {
        assertFalse(canRemoveResidualLegacyBlock(loadedSimplifiedCount = 0, legacyBlockCount = 1))
        assertTrue(canRemoveResidualLegacyBlock(loadedSimplifiedCount = 0, legacyBlockCount = 2))
        assertTrue(canRemoveResidualLegacyBlock(loadedSimplifiedCount = 1, legacyBlockCount = 1))
    }

    @Test
    fun `buildLoadedAvailableBlockState in edit mode excludes edited session and shows hint while hydrated block suppresses hint`() {
        val frontSquatBlock = AvailableBlock(
            id = 11L,
            title = "Front Squat",
            instructions = "3x5",
            exercises = listOf(AvailableBlockExercise(frontSquat.id, frontSquat.name, true)),
            trackedPrs = listOf(TrackedPrSlot(frontSquat.id, frontSquat.name, 5))
        )
        val rdlBlock = AvailableBlock(
            id = 10L,
            title = "Romanian Deadlift",
            instructions = "",
            exercises = listOf(AvailableBlockExercise(rdl.id, rdl.name, true)),
            trackedPrs = emptyList()
        )
        val priorSession = SessionWithBlocks(
            session = Session(
                id = 10L,
                cycleId = 1L,
                date = LocalDate.of(2026, 11, 7),
                title = "Prior"
            ),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 101L,
                        sessionId = 10L,
                        position = 0,
                        name = frontSquat.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = frontSquat.id,
                        routineId = 11L,
                        subBlock = "Front Squat"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 101L, position = 0, reps = 5, weight = 70.0),
                        BlockSet(blockId = 101L, position = 1, reps = 5, weight = 70.0)
                    )
                )
            )
        )
        val editedSession = SessionWithBlocks(
            session = Session(
                id = 20L,
                cycleId = 1L,
                date = LocalDate.of(2026, 11, 14),
                title = "Editing"
            ),
            blocks = listOf(
                BlockWithSets(
                    block = SessionBlock(
                        id = 201L,
                        sessionId = 20L,
                        position = 0,
                        name = rdl.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = rdl.id,
                        routineId = 10L,
                        subBlock = "Romanian Deadlift"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 201L, position = 0, reps = 5, weight = 82.5)
                    )
                ),
                // Even if session 20 also has a Front Squat weight, editingSessionId = 20L excludes it from pre-fill lookup.
                BlockWithSets(
                    block = SessionBlock(
                        id = 202L,
                        sessionId = 20L,
                        position = 1,
                        name = frontSquat.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = frontSquat.id,
                        routineId = 11L,
                        subBlock = "Front Squat"
                    ),
                    sets = listOf(
                        BlockSet(blockId = 202L, position = 0, reps = 5, weight = 99.0)
                    )
                )
            )
        )

        val hydrated = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = editedSession,
            availableBlocks = listOf(rdlBlock, frontSquatBlock),
            exercises = listOf(rdl, frontSquat),
            sessionRepMaxes = emptyList()
        )
        val originalCard = buildHydratedBlockState(
            seed = hydrated.simplifiedBlocks.first(),
            exercises = listOf(rdl, frontSquat),
            editingSessionId = 20L
        )
        assertNull(originalCard.exerciseSections.first().lastSessionDateLabel)

        val newlyAddedSavedCard = buildLoadedAvailableBlockState(
            block = frontSquatBlock,
            sessions = listOf(editedSession, priorSession),
            exercises = listOf(rdl, frontSquat),
            weightUnit = "kg",
            isEditMode = true,
            editingSessionId = 20L
        )
        assertTrue(newlyAddedSavedCard.isEditMode)
        assertEquals(20L, newlyAddedSavedCard.editingSessionId)
        assertEquals(listOf("70", "70"), newlyAddedSavedCard.exerciseSections.first().weights.toList())
        val hint = newlyAddedSavedCard.exerciseSections.first().lastSessionDateLabel
        assertNotNull(hint)
        assertTrue(hint!!.startsWith("Pre-filled from last session ("))
    }

    @Test
    fun `buildCustomAvailableBlockState initializes 3 empty tiles, unique uiKey, and ignores parent PR inheritance`() {
        val customBlock = AvailableBlock(
            id = 0L,
            title = "Accessories",
            instructions = "3x10",
            exercises = listOf(
                AvailableBlockExercise(walkingLunge.id, walkingLunge.name, true),
                AvailableBlockExercise(frontSquat.id, frontSquat.name, true)
            ),
            trackedPrs = emptyList()
        )

        val first = buildCustomAvailableBlockState(customBlock)
        val second = buildCustomAvailableBlockState(customBlock)

        assertNotEquals(first.uiKey, second.uiKey)
        assertEquals(2, first.exerciseSections.size)
        first.exerciseSections.forEach { section ->
            assertNull(section.lastSessionDateLabel)
            assertEquals(listOf("", "", ""), section.weights.toList())
        }
        assertTrue(first.trackedPrSlots.isEmpty())

        first.trackedPrSlots.add(TrackedPrSlot(walkingLunge.id, walkingLunge.name, 10))
        assertNull(first.inheritedTrackedPrSlotsForParent(listOf(walkingLunge, frontSquat)))
    }

    @Test
    fun `Repository prepareCustomAvailableBlock isolates session-only blocks from Library and optionally saves to Library`() = runTest {
        val bench = repository.getOrCreateExercise("Bench Press")
        val row = repository.getOrCreateExercise("Bent Over Row")

        val sessionOnly = repository.prepareCustomAvailableBlock(
            rawTitle = "",
            instructions = "Superset 3x8",
            selectedExerciseIds = listOf(bench.id, row.id),
            saveToLibrary = false
        )
        assertNotNull(sessionOnly)
        assertEquals(0L, sessionOnly!!.id)
        assertEquals("Bench Press + Bent Over Row", sessionOnly.title)
        assertEquals(2, sessionOnly.exercises.size)
        assertTrue(fakeDb.routineDao().getAllOnce().isEmpty())

        val zeroChipSessionOnly = repository.prepareCustomAvailableBlock(
            rawTitle = "Push Press",
            instructions = "Heavy triples",
            selectedExerciseIds = emptyList(),
            saveToLibrary = false
        )
        assertNotNull(zeroChipSessionOnly)
        assertEquals(0L, zeroChipSessionOnly!!.id)
        assertEquals("Push Press", zeroChipSessionOnly.title)
        assertEquals(1, zeroChipSessionOnly.exercises.size)
        val autoCreated = repository.getAllExercisesOnce().firstOrNull { it.name == "Push Press" }
        assertNotNull(autoCreated)
        assertEquals(MetricType.WEIGHT, autoCreated!!.metricType)
        assertTrue(autoCreated.tracksRepMax)
        assertTrue(fakeDb.routineDao().getAllOnce().isEmpty())

        val savedBlock = repository.prepareCustomAvailableBlock(
            rawTitle = "Upper Accessory",
            instructions = "3x10",
            selectedExerciseIds = listOf(row.id),
            saveToLibrary = true
        )
        assertNotNull(savedBlock)
        assertTrue(savedBlock!!.id > 0L)
        assertEquals("Upper Accessory", savedBlock.title)
        val routines = repository.getAllRoutinesWithBlocksOnce()
        assertEquals(1, routines.size)
        assertEquals(savedBlock.id, routines.first().routine.id)
    }

    @Test
    fun `editSession persists hydrated block plus newly added saved and session-only custom blocks with PR reconciliation`() = runTest {
        val cycleId = repository.saveCycle(
            Cycle(
                name = "Strength Autumn",
                startDate = LocalDate.of(2026, 11, 1),
                isActive = true
            )
        )
        val rdlEx = repository.getOrCreateExercise("Romanian Deadlift")
        val squatEx = repository.getOrCreateExercise("Front Squat")
        val lungeEx = repository.getOrCreateExercise("Walking Lunge")
        val allExercises = listOf(rdlEx, squatEx, lungeEx)

        val rdlBlockId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Romanian Deadlift",
                instructions = "",
                exercises = listOf(AvailableBlockExercise(rdlEx.id, rdlEx.name, true)),
                trackedPrs = listOf(TrackedPrSlot(rdlEx.id, rdlEx.name, 5))
            )
        )
        val squatBlockId = repository.saveAvailableBlock(
            AvailableBlock(
                id = 0L,
                title = "Front Squat",
                instructions = "3x5",
                exercises = listOf(AvailableBlockExercise(squatEx.id, squatEx.name, true)),
                trackedPrs = listOf(TrackedPrSlot(squatEx.id, squatEx.name, 5))
            )
        )

        val sessionDate = LocalDate.of(2026, 11, 15)
        val sessionId = repository.saveSession(
            session = Session(cycleId = cycleId, date = sessionDate, title = "Lower Strength"),
            blocks = listOf(
                BlockInsert(
                    block = SessionBlock(
                        sessionId = 0L,
                        position = 0,
                        name = rdlEx.name,
                        kind = BlockKind.STRENGTH,
                        mainExerciseId = rdlEx.id,
                        routineId = rdlBlockId,
                        subBlock = "Romanian Deadlift",
                        isCompleted = true
                    ),
                    sets = listOf(
                        BlockSet(blockId = 0L, position = 0, reps = 5, weight = 82.5),
                        BlockSet(blockId = 0L, position = 1, reps = 5, weight = 75.0),
                        BlockSet(blockId = 0L, position = 2, reps = 5, weight = 75.0)
                    ),
                    newRepMaxes = listOf(
                        RepMax(exerciseId = rdlEx.id, reps = 5, weight = 82.5, date = sessionDate)
                    )
                )
            )
        )

        val initialRdlPrId = fakeDb.repMaxDao().getAllOnce().single().id
        val allRoutines = repository.getAllRoutinesWithBlocksOnce()
        val availableBlocks = allRoutines.map { AvailableBlockCodec.fromRoutineWithBlocks(it, allExercises) }
        val rdlBlock = availableBlocks.first { it.id == rdlBlockId }
        val squatBlock = availableBlocks.first { it.id == squatBlockId }

        val editingSession = repository.sessionById(sessionId)!!
        val initialRepMaxes = fakeDb.repMaxDao().getAllOnce()
        val hydrated = AvailableBlockCodec.hydrateSessionBlocksForEditor(
            session = editingSession,
            availableBlocks = listOf(rdlBlock, squatBlock),
            exercises = allExercises,
            sessionRepMaxes = initialRepMaxes.filter { it.sessionId == sessionId }
        )
        val hydratedRdlState = buildHydratedBlockState(
            seed = hydrated.simplifiedBlocks.single(),
            exercises = allExercises,
            editingSessionId = sessionId
        )

        val addedSavedSquatState = buildLoadedAvailableBlockState(
            block = squatBlock,
            sessions = repository.getAllSessionsWithBlocksOnce(),
            exercises = allExercises,
            weightUnit = "kg",
            isEditMode = true,
            editingSessionId = sessionId
        )
        addedSavedSquatState.updateSetWeight(addedSavedSquatState.exerciseSections.single(), 0, "95")

        val preparedCustomLunge = repository.prepareCustomAvailableBlock(
            rawTitle = "",
            instructions = "Controlled eccentric",
            selectedExerciseIds = listOf(lungeEx.id),
            saveToLibrary = false
        )!!
        val addedCustomLungeState = buildCustomAvailableBlockState(
            block = preparedCustomLunge,
            isEditMode = true,
            editingSessionId = sessionId
        )
        addedCustomLungeState.updateSetWeight(addedCustomLungeState.exerciseSections.single(), 0, "40")

        val drafts = listOf(hydratedRdlState, addedSavedSquatState, addedCustomLungeState)
            .flatMap { it.toBlockDrafts(allExercises, initialRepMaxes) }
        assertEquals(3, drafts.size)

        val inserts = drafts.mapIndexed { idx, draft ->
            BlockInsert(
                block = SessionBlock(
                    sessionId = sessionId,
                    position = idx,
                    name = draft.name,
                    kind = draft.kind,
                    format = draft.format,
                    scheme = draft.scheme,
                    mainExerciseId = draft.existingExerciseId,
                    routineId = draft.routineId,
                    description = draft.description,
                    section = draft.section,
                    exerciseIdsCsv = draft.exerciseIdsCsv,
                    subBlock = draft.subBlock,
                    isCompleted = draft.isCompleted
                ),
                sets = draft.sets.mapIndexed { setIdx, setDraft ->
                    BlockSet(blockId = 0L, position = setIdx, reps = setDraft.reps, weight = setDraft.weight)
                },
                newRepMaxes = draft.newRepMaxes.map { rmDraft ->
                    RepMax(
                        exerciseId = rmDraft.exerciseId,
                        reps = rmDraft.reps,
                        weight = rmDraft.weight,
                        date = sessionDate
                    )
                }
            )
        }

        repository.updateSession(
            session = editingSession.session,
            blocks = inserts,
            reconcileRepMaxes = true,
            managedRepMaxIds = managedRepMaxIdsOf(hydrated)
        )

        val reloaded = repository.sessionById(sessionId)!!
        assertEquals("Lower Strength", reloaded.session.title)
        assertEquals(3, reloaded.blocks.size)
        assertEquals(rdlBlockId, reloaded.blocks[0].block.routineId)
        assertEquals(squatBlockId, reloaded.blocks[1].block.routineId)
        assertNull(reloaded.blocks[2].block.routineId)
        assertEquals("Walking Lunge", reloaded.blocks[2].block.name)
        assertEquals(listOf(40.0), reloaded.blocks[2].sets.map { it.weight })

        val persistedPrs = fakeDb.repMaxDao().getAllOnce().filter { it.sessionId == sessionId }
        assertEquals(2, persistedPrs.size)
        val preservedRdlPr = persistedPrs.first { it.exerciseId == rdlEx.id }
        assertEquals(initialRdlPrId, preservedRdlPr.id)
        val addedSquatPr = persistedPrs.first { it.exerciseId == squatEx.id }
        assertEquals(95.0, addedSquatPr.weight, 0.001)

        // Ensure session-only custom block did not add a Library routine
        assertEquals(2, fakeDb.routineDao().getAllOnce().size)
    }
}
