package com.fractanomics.crosstraining.ui.draft

import com.fractanomics.crosstraining.ui.navigation.BottomDestination
import com.fractanomics.crosstraining.ui.navigation.DrawerItem
import com.fractanomics.crosstraining.ui.timer.TimerPhase
import com.fractanomics.crosstraining.ui.timer.TimerSnapshot
import com.fractanomics.crosstraining.ui.timer.WorkoutTimerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionDockResolverTest {

    private val idleSummary = ActiveWorkoutSummary(isActive = false)
    private val activeSummary = ActiveWorkoutSummary(
        isActive = true,
        displayTitle = "Push Day",
        blockCount = 4,
        loggedBlockCount = 2,
        loggedSetCount = 7,
        blockKeys = setOf("blk-1", "blk-2")
    )

    private val idleTimer = TimerSnapshot(phase = TimerPhase.IDLE)
    private val linkedWorkTimer = TimerSnapshot(
        phase = TimerPhase.WORK,
        isRunning = true,
        activeConfig = WorkoutTimerConfig(sourceBlockKey = "blk-1", workoutLabel = "Bench Press")
    )
    private val standaloneWorkTimer = TimerSnapshot(
        phase = TimerPhase.WORK,
        isRunning = true,
        activeConfig = WorkoutTimerConfig(sourceBlockKey = null, workoutLabel = "")
    )
    private val finishedStandaloneTimer = TimerSnapshot(
        phase = TimerPhase.FINISHED,
        isRunning = false,
        activeConfig = WorkoutTimerConfig(sourceBlockKey = null)
    )

    @Test
    fun `log route never renders global dock and owns inner timer bar exclusively`() {
        val noTimer = LiveSessionDockResolver.resolve(BottomDestination.LOG.route, activeSummary, idleTimer)
        assertEquals(LiveDockMode.NONE, noTimer.mode)
        assertFalse(noTimer.showLogNavBadge)
        assertTrue(noTimer.showDrawerActiveBadge)
        assertFalse(noTimer.showInnerEditorTimerBar)

        val withTimer = LiveSessionDockResolver.resolve(BottomDestination.LOG.route, activeSummary, linkedWorkTimer)
        assertEquals(LiveDockMode.NONE, withTimer.mode)
        assertFalse(withTimer.showLogNavBadge)
        assertTrue(withTimer.showDrawerActiveBadge)
        assertTrue(withTimer.showInnerEditorTimerBar)
    }

    @Test
    fun `timer route shows workout-only dock when active and hides timer controls`() {
        val withDraftAndTimer = LiveSessionDockResolver.resolve(DrawerItem.TIMER.route, activeSummary, linkedWorkTimer)
        assertEquals(LiveDockMode.WORKOUT_ONLY, withDraftAndTimer.mode)
        assertTrue(withDraftAndTimer.showLogNavBadge)
        assertTrue(withDraftAndTimer.showDrawerActiveBadge)
        assertFalse(withDraftAndTimer.showInnerEditorTimerBar)

        val noDraftWithTimer = LiveSessionDockResolver.resolve(DrawerItem.TIMER.route, idleSummary, standaloneWorkTimer)
        assertEquals(LiveDockMode.NONE, noDraftWithTimer.mode)
        assertFalse(noDraftWithTimer.showLogNavBadge)
        assertFalse(noDraftWithTimer.showDrawerActiveBadge)
    }

    @Test
    fun `non-log non-timer routes arbitrate all four dock modes cleanly`() {
        val routes = listOf(
            BottomDestination.HISTORY.route,
            BottomDestination.PROGRESS.route,
            BottomDestination.CYCLES.route,
            BottomDestination.LIBRARY.route,
            DrawerItem.PROFILE.route,
            "sessionEditor/42/false",
            "sessionEditor/42/true"
        )
        routes.forEach { route ->
            val neither = LiveSessionDockResolver.resolve(route, idleSummary, idleTimer)
            assertEquals("Route $route neither", LiveDockMode.NONE, neither.mode)
            assertFalse(neither.showInnerEditorTimerBar)

            val workoutOnly = LiveSessionDockResolver.resolve(route, activeSummary, idleTimer)
            assertEquals("Route $route workoutOnly", LiveDockMode.WORKOUT_ONLY, workoutOnly.mode)
            assertTrue(workoutOnly.showLogNavBadge)
            assertTrue(workoutOnly.showDrawerActiveBadge)
            assertFalse(workoutOnly.showInnerEditorTimerBar)

            val timerOnly = LiveSessionDockResolver.resolve(route, idleSummary, standaloneWorkTimer)
            assertEquals("Route $route timerOnly", LiveDockMode.TIMER_ONLY, timerOnly.mode)
            assertFalse(timerOnly.showLogNavBadge)
            assertFalse(timerOnly.isTimerLinkedToWorkout)
            assertFalse(timerOnly.showInnerEditorTimerBar)

            val bothLinked = LiveSessionDockResolver.resolve(route, activeSummary, linkedWorkTimer)
            assertEquals("Route $route bothLinked", LiveDockMode.WORKOUT_AND_TIMER, bothLinked.mode)
            assertTrue(bothLinked.isTimerLinkedToWorkout)
            assertTrue(bothLinked.showLogNavBadge)
            assertFalse(bothLinked.showInnerEditorTimerBar)

            val bothStandalone = LiveSessionDockResolver.resolve(route, activeSummary, standaloneWorkTimer)
            assertEquals("Route $route bothStandalone", LiveDockMode.WORKOUT_AND_TIMER, bothStandalone.mode)
            assertFalse(bothStandalone.isTimerLinkedToWorkout)
        }
    }

    @Test
    fun `subtitle and accessibility label format cleanly for active drafts`() {
        val state = LiveSessionDockResolver.resolve(BottomDestination.HISTORY.route, activeSummary, idleTimer)
        assertEquals("Push Day", state.workoutTitle)
        assertEquals("2/4 blocks · 7 sets", state.workoutSubtitle)
        assertEquals(
            "Workout in progress: Push Day, 2/4 blocks · 7 sets. Double tap to resume.",
            state.workoutAccessibilityLabel
        )

        val zeroSetState = LiveSessionDockResolver.resolve(
            BottomDestination.HISTORY.route,
            activeSummary.copy(loggedBlockCount = 0, loggedSetCount = 0, blockCount = 3),
            idleTimer
        )
        assertEquals("3 blocks ready", zeroSetState.workoutSubtitle)

        val titleOnlyState = LiveSessionDockResolver.resolve(
            BottomDestination.HISTORY.route,
            activeSummary.copy(loggedBlockCount = 0, loggedSetCount = 0, blockCount = 0),
            idleTimer
        )
        assertEquals("Draft in progress", titleOnlyState.workoutSubtitle)
    }

    @Test
    fun `shouldStopTimerOnDraftReset stops linked timers on discard and finished timers on save`() {
        val keys = setOf("blk-1", "blk-2")

        // Discard: stops linked, keeps standalone
        assertTrue(LiveSessionDockResolver.shouldStopTimerOnDraftReset(linkedWorkTimer, keys, isSaveCompletion = false))
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(standaloneWorkTimer, keys, isSaveCompletion = false))
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(finishedStandaloneTimer, keys, isSaveCompletion = false))

        // Save: stops linked AND finished timers, keeps running standalone timer
        assertTrue(LiveSessionDockResolver.shouldStopTimerOnDraftReset(linkedWorkTimer, keys, isSaveCompletion = true))
        assertTrue(LiveSessionDockResolver.shouldStopTimerOnDraftReset(finishedStandaloneTimer, keys, isSaveCompletion = true))
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(standaloneWorkTimer, keys, isSaveCompletion = true))
    }

    @Test
    fun `timers linked to blocks outside the draft are never treated as draft-owned`() {
        val foreignTimer = linkedWorkTimer.copy(
            activeConfig = linkedWorkTimer.activeConfig.copy(sourceBlockKey = "history-blk-9")
        )

        // Title-only draft (no block keys) must not claim or stop unrelated block-linked timers.
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(linkedWorkTimer, emptySet(), isSaveCompletion = false))
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(foreignTimer, setOf("blk-1"), isSaveCompletion = false))
        assertFalse(LiveSessionDockResolver.shouldStopTimerOnDraftReset(foreignTimer, setOf("blk-1"), isSaveCompletion = true))

        val titleOnlySummary = activeSummary.copy(blockCount = 0, loggedBlockCount = 0, loggedSetCount = 0, blockKeys = emptySet())
        val titleOnlyState = LiveSessionDockResolver.resolve(BottomDestination.HISTORY.route, titleOnlySummary, linkedWorkTimer)
        assertEquals(LiveDockMode.WORKOUT_AND_TIMER, titleOnlyState.mode)
        assertFalse(titleOnlyState.isTimerLinkedToWorkout)

        val foreignState = LiveSessionDockResolver.resolve(BottomDestination.HISTORY.route, activeSummary, foreignTimer)
        assertFalse(foreignState.isTimerLinkedToWorkout)
    }
}
