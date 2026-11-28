package com.fractanomics.crosstraining.ui.screens

import com.fractanomics.crosstraining.data.model.Exercise
import com.fractanomics.crosstraining.data.model.Routine
import com.fractanomics.crosstraining.ui.draft.ActiveLegacyBlockSnapshot
import com.fractanomics.crosstraining.ui.draft.ActiveLegacySetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC-032 cold-restart guard: the Log draft is restored before Room emits the exercise /
 * routine library, so unresolved legacy references must survive the first auto-save and
 * re-resolve once the library arrives.
 */
class ActiveDraftLegacyRestoreTest {

    private val squat = Exercise(id = 10L, name = "Back Squat")
    private val lunge = Exercise(id = 11L, name = "Walking Lunge")
    private val routine = Routine(id = 7L, name = "Leg Complex")

    private val persisted = ActiveLegacyBlockSnapshot(
        name = "Back Squat",
        exerciseId = 10L,
        routineId = 7L,
        sequenceExerciseIds = listOf(10L, 11L),
        sets = listOf(ActiveLegacySetSnapshot(reps = "5", value = "100"))
    )

    @Test
    fun `restore before library load keeps persisted ids in the next auto-save snapshot`() {
        val state = restoreLegacyBlockState(persisted, exercises = emptyList(), routines = emptyList())

        assertNull(state.exercise)
        assertTrue(persisted.hasUnresolvedLibraryRefs(state))

        val degraded = state.toActiveLegacyBlockSnapshot()
        assertNull(degraded.exerciseId)

        val saved = degraded.withPreservedLibraryRefs(persisted)
        assertEquals(10L, saved.exerciseId)
        assertEquals(7L, saved.routineId)
        assertEquals(listOf(10L, 11L), saved.sequenceExerciseIds)
        assertEquals("5", saved.sets.single().reps)
    }

    @Test
    fun `resolveLibraryRefs hydrates missing references once the library arrives`() {
        val state = restoreLegacyBlockState(persisted, exercises = emptyList(), routines = emptyList())

        state.resolveLibraryRefs(persisted, exercises = listOf(squat, lunge), routines = listOf(routine))

        assertEquals(squat, state.exercise)
        assertEquals(routine, state.routine)
        assertEquals(listOf(squat, lunge), state.sequenceExercises.toList())
        assertFalse(persisted.hasUnresolvedLibraryRefs(state))
        assertEquals(persisted, state.toActiveLegacyBlockSnapshot())
    }

    @Test
    fun `resolveLibraryRefs never overrides an exercise the athlete picked meanwhile`() {
        val state = restoreLegacyBlockState(persisted, exercises = emptyList(), routines = emptyList())
        state.exercise = lunge

        state.resolveLibraryRefs(persisted, exercises = listOf(squat, lunge), routines = listOf(routine))

        assertEquals(lunge, state.exercise)
        assertEquals(11L, state.toActiveLegacyBlockSnapshot().withPreservedLibraryRefs(persisted).exerciseId)
    }
}
