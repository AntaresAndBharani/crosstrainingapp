package com.fractanomics.crosstraining.ui.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousTimerTest {

    private fun TestScope.createEngine(): TimerEngine =
        TimerEngine(context = null, coroutineDispatcher = UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.tickSeconds(seconds: Int) {
        repeat(seconds) {
            advanceTimeBy(1_000L)
            runCurrent()
        }
    }

    @Test
    fun `group 1 - pre-plan and live mode chaining flows through TRANSITION, promotes stage, frees queue slot for stage 3`() = runTest {
        val engine = createEngine()

        val stage1 = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 10,
            totalRounds = 2,
            prepCountdownSeconds = 0
        )
        val stage2 = WorkoutTimerConfig(
            mode = TimerMode.AMRAP,
            targetMinutes = 1,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )
        val stage3 = WorkoutTimerConfig(
            mode = TimerMode.REST,
            restSeconds = 15,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )

        engine.configure(stage1, QueuedTimerStage(config = stage2, transitionSeconds = 60))
        assertEquals(TimerPhase.IDLE, engine.snapshot.value.phase)
        assertNotNull(engine.snapshot.value.queuedNextStage)
        assertEquals(60 + 20 + 60, engine.snapshot.value.totalSecondsRemaining)

        engine.start()
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)
        assertEquals(0, engine.snapshot.value.completedStagesCount)

        // Complete Round 1 (10s) and Round 2 (10s) -> enters TRANSITION
        tickSeconds(20)
        val transitionSnap = engine.snapshot.value
        assertEquals(TimerPhase.TRANSITION, transitionSnap.phase)
        assertTrue(transitionSnap.isRunning)
        assertEquals(60, transitionSnap.roundSecondsRemaining)
        assertEquals(0, transitionSnap.completedStagesCount)
        assertEquals(TimerMode.AMRAP, transitionSnap.queuedNextStage?.config?.mode)

        // Complete 60s transition -> promotes Stage 2 (AMRAP), frees queuedNextStage for Stage 3
        tickSeconds(60)
        val stage2Snap = engine.snapshot.value
        assertEquals(TimerPhase.WORK, stage2Snap.phase)
        assertEquals(TimerMode.AMRAP, stage2Snap.mode)
        assertTrue(stage2Snap.isRunning)
        assertEquals(1, stage2Snap.currentRound)
        assertEquals(60, stage2Snap.roundSecondsRemaining)
        assertNull(stage2Snap.queuedNextStage)
        assertEquals(1, stage2Snap.completedStagesCount)

        // Queue Stage 3 live mid-Stage 2, skip Stage 2 -> finishes via 0s / queues cleanly
        engine.setQueuedNextStage(QueuedTimerStage(config = stage3, transitionSeconds = 0))
        assertEquals(TimerMode.REST, engine.snapshot.value.queuedNextStage?.config?.mode)

        engine.skipRound()
        val stage3Snap = engine.snapshot.value
        assertEquals(TimerPhase.WORK, stage3Snap.phase)
        assertEquals(TimerMode.REST, stage3Snap.mode)
        assertEquals(2, stage3Snap.completedStagesCount)
        assertNull(stage3Snap.queuedNextStage)

        engine.release()
    }

    @Test
    fun `group 2 - zero second transition bypasses TRANSITION and promotes stage 2 immediately`() = runTest {
        val engine = createEngine()

        val stage1 = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 5,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )
        val stage2 = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 20,
            restSeconds = 10,
            totalRounds = 4,
            prepCountdownSeconds = 0
        )

        engine.configure(stage1, QueuedTimerStage(config = stage2, transitionSeconds = 0))
        engine.start()

        tickSeconds(5)
        val promoted = engine.snapshot.value
        assertEquals(TimerPhase.WORK, promoted.phase)
        assertEquals(TimerMode.TABATA, promoted.mode)
        assertEquals(1, promoted.currentRound)
        assertEquals(4, promoted.totalRounds)
        assertEquals(20, promoted.roundSecondsRemaining)
        assertEquals(10, promoted.activeRoundRestSeconds)
        assertEquals(1, promoted.completedStagesCount)
        assertNull(promoted.queuedNextStage)

        engine.release()
    }

    @Test
    fun `group 3 - mid-transition live edits, config update, and Start Now while paused`() = runTest {
        val engine = createEngine()

        val stage1 = WorkoutTimerConfig(
            mode = TimerMode.REST,
            restSeconds = 5,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )
        val stage2 = WorkoutTimerConfig(
            mode = TimerMode.AMRAP,
            targetMinutes = 5,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )
        val replacementStage2 = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 30,
            totalRounds = 4,
            prepCountdownSeconds = 0
        )

        engine.configure(stage1, QueuedTimerStage(stage2, transitionSeconds = 60))
        engine.start()
        tickSeconds(5)
        assertEquals(TimerPhase.TRANSITION, engine.snapshot.value.phase)
        assertEquals(60, engine.snapshot.value.roundSecondsRemaining)

        // Adjust +15s and -15s, then set exact transition time
        engine.adjustTransitionRemaining(15)
        assertEquals(75, engine.snapshot.value.roundSecondsRemaining)
        engine.setTransitionRemaining(25)
        assertEquals(25, engine.snapshot.value.roundSecondsRemaining)

        // Swap upcoming queued mode live during TRANSITION
        engine.updateQueuedNextConfig(replacementStage2)
        assertEquals(TimerMode.EMOM, engine.snapshot.value.queuedNextStage?.config?.mode)

        // Pause during transition, then Start Now (skipRound) must promote & resume running!
        engine.pause()
        assertFalse(engine.snapshot.value.isRunning)
        engine.skipRound()

        val stage2Running = engine.snapshot.value
        assertEquals(TimerPhase.WORK, stage2Running.phase)
        assertEquals(TimerMode.EMOM, stage2Running.mode)
        assertTrue(stage2Running.isRunning)
        assertEquals(30, stage2Running.roundSecondsRemaining)
        assertEquals(1, stage2Running.completedStagesCount)

        engine.release()
    }

    @Test
    fun `group 4 - single-round Next Round overrides apply only to R(c+1) and Tabata 0s rest skips REST`() = runTest {
        val engine = createEngine()

        // 4A: EMOM single-round override
        val emom = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 10,
            totalRounds = 4,
            prepCountdownSeconds = 0
        )
        engine.configure(emom)
        engine.start()
        assertEquals(10, engine.snapshot.value.roundSecondsRemaining)

        // Add +15s to R2 (so R2 = 25s); R1 stays at 10s, total remaining reflects +15s
        engine.adjustNextRoundDuration(15)
        assertEquals(25, engine.snapshot.value.nextRoundOverrideSeconds)
        assertEquals(10 + 25 + 10 + 10, engine.snapshot.value.totalSecondsRemaining)

        // Advance to R2 -> consumes override, R2 runs 25s, override cleared for R3!
        tickSeconds(10)
        assertEquals(2, engine.snapshot.value.currentRound)
        assertEquals(25, engine.snapshot.value.roundSecondsRemaining)
        assertEquals(25, engine.snapshot.value.roundTotalSeconds)
        assertNull(engine.snapshot.value.nextRoundOverrideSeconds)

        // Advance past R2 (25s) -> R3 uses base 10s!
        tickSeconds(25)
        assertEquals(3, engine.snapshot.value.currentRound)
        assertEquals(10, engine.snapshot.value.roundSecondsRemaining)
        assertEquals(10, engine.snapshot.value.roundTotalSeconds)

        // Minimum floor >= 5s check on Next Round override
        engine.setNextRoundDuration(1)
        assertEquals(5, engine.snapshot.value.nextRoundOverrideSeconds)

        // 4B: Tabata Next Work + 0s Next Rest override skips REST in R2 then reverts in R3
        val tabata = WorkoutTimerConfig(
            mode = TimerMode.TABATA,
            workSeconds = 10,
            restSeconds = 5,
            totalRounds = 3,
            prepCountdownSeconds = 0
        )
        engine.replaceTimer(tabata)
        assertEquals(1, engine.snapshot.value.currentRound)

        engine.setNextRoundDuration(12)
        engine.setNextRoundRestDuration(0)
        assertEquals(12, engine.snapshot.value.nextRoundOverrideSeconds)
        assertEquals(0, engine.snapshot.value.nextRoundRestOverrideSeconds)

        // Complete R1 WORK (10s) -> R1 REST (5s) -> enters R2 WORK (12s, rest = 0s, overrides consumed)
        tickSeconds(10)
        assertEquals(TimerPhase.REST, engine.snapshot.value.phase)
        assertEquals(1, engine.snapshot.value.currentRound)
        tickSeconds(5)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(2, engine.snapshot.value.currentRound)
        assertEquals(12, engine.snapshot.value.roundSecondsRemaining)
        assertEquals(0, engine.snapshot.value.activeRoundRestSeconds)
        assertNull(engine.snapshot.value.nextRoundOverrideSeconds)
        assertNull(engine.snapshot.value.nextRoundRestOverrideSeconds)

        // Complete R2 WORK (12s) -> skips R2 REST directly into R3 WORK (base 10s work, base 5s rest)
        tickSeconds(12)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(3, engine.snapshot.value.currentRound)
        assertEquals(10, engine.snapshot.value.roundSecondsRemaining)
        assertEquals(5, engine.snapshot.value.activeRoundRestSeconds)

        engine.release()
    }

    @Test
    fun `group 5 - all 6 modes support multi-round execution and adjustTotalRounds clamps and purges overrides`() = runTest {
        val engine = createEngine()

        // Verify AMRAP, TIME_CAP, DEATH_BY, REST, EMOM, TABATA all run multi-round sequentially
        val multiConfigs = listOf(
            WorkoutTimerConfig(mode = TimerMode.AMRAP, targetDurationSeconds = 6, totalRounds = 2, prepCountdownSeconds = 0),
            WorkoutTimerConfig(mode = TimerMode.TIME_CAP, targetDurationSeconds = 6, totalRounds = 2, prepCountdownSeconds = 0),
            WorkoutTimerConfig(mode = TimerMode.DEATH_BY, intervalSeconds = 6, totalRounds = 2, prepCountdownSeconds = 0),
            WorkoutTimerConfig(mode = TimerMode.REST, restSeconds = 6, totalRounds = 2, prepCountdownSeconds = 0),
            WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 6, totalRounds = 2, prepCountdownSeconds = 0),
            WorkoutTimerConfig(mode = TimerMode.TABATA, workSeconds = 6, restSeconds = 0, totalRounds = 2, prepCountdownSeconds = 0)
        )

        multiConfigs.forEach { cfg ->
            engine.replaceTimer(cfg)
            assertEquals(1, engine.snapshot.value.currentRound)
            assertEquals(2, engine.snapshot.value.totalRounds)
            tickSeconds(6)
            assertEquals("${cfg.mode} should advance to round 2", 2, engine.snapshot.value.currentRound)
            assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
            tickSeconds(6)
            assertEquals("${cfg.mode} should finish after round 2", TimerPhase.FINISHED, engine.snapshot.value.phase)
        }

        // Verify lower bound clamp >= currentRound AND purge overrides when shrunk down to currentRound
        engine.replaceTimer(
            WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 20, totalRounds = 3, prepCountdownSeconds = 0)
        )
        engine.skipRound() // Now at Round 2 of 3
        assertEquals(2, engine.snapshot.value.currentRound)

        engine.setNextRoundDuration(35)
        assertEquals(35, engine.snapshot.value.nextRoundOverrideSeconds)

        // Attempt to shrink below currentRound -> clamped to currentRound (2) AND purges R3 override
        engine.setTotalRounds(1)
        assertEquals(2, engine.snapshot.value.totalRounds)
        assertNull(engine.snapshot.value.nextRoundOverrideSeconds)

        // Incrementing rounds back to 3 unlocks R3 starting clean at base duration (null override)
        engine.adjustTotalRounds(1)
        assertEquals(3, engine.snapshot.value.totalRounds)
        assertNull(engine.snapshot.value.nextRoundOverrideSeconds)

        engine.release()
    }

    @Test
    fun `group 6 - continueFromFinished, restartCurrentStage preserves queue, and stopAndClearAll resets all`() = runTest {
        val engine = createEngine()

        val stage1 = WorkoutTimerConfig(
            mode = TimerMode.REST,
            restSeconds = 5,
            totalRounds = 1,
            prepCountdownSeconds = 0
        )
        val stage2 = WorkoutTimerConfig(
            mode = TimerMode.EMOM,
            intervalSeconds = 10,
            totalRounds = 3,
            prepCountdownSeconds = 0
        )

        engine.configure(stage1)
        engine.start()
        tickSeconds(5)
        assertEquals(TimerPhase.FINISHED, engine.snapshot.value.phase)

        // Add another mode directly (0s transition) -> increments completedStagesCount & runs Stage 2
        engine.continueFromFinished(stage2, transitionSeconds = 0)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals(TimerMode.EMOM, engine.snapshot.value.mode)
        assertEquals(1, engine.snapshot.value.completedStagesCount)

        // Queue Stage 3 and advance to Round 2, then restartCurrentStage()
        val stage3 = WorkoutTimerConfig(mode = TimerMode.REST, restSeconds = 20, totalRounds = 1, prepCountdownSeconds = 0)
        engine.setQueuedNextStage(QueuedTimerStage(stage3, transitionSeconds = 30))
        engine.skipRound()
        assertEquals(2, engine.snapshot.value.currentRound)

        engine.restartCurrentStage()
        val restarted = engine.snapshot.value
        assertEquals(TimerPhase.WORK, restarted.phase)
        assertTrue(restarted.isRunning)
        assertEquals(1, restarted.currentRound)
        assertEquals(1, restarted.completedStagesCount)
        assertEquals(TimerMode.REST, restarted.queuedNextStage?.config?.mode)

        // stopAndClearAll wipes queue, overrides, and completedStagesCount -> IDLE
        engine.stopAndClearAll()
        val cleared = engine.snapshot.value
        assertEquals(TimerPhase.IDLE, cleared.phase)
        assertFalse(cleared.isRunning)
        assertNull(cleared.queuedNextStage)
        assertEquals(0, cleared.completedStagesCount)

        engine.release()
    }

    @Test
    fun `group 7 - ContinuousTimerUiResolver clocks, skip labels, reset confirm, live state, and polite a11y boundary`() {
        val timeCapConfig = WorkoutTimerConfig(mode = TimerMode.TIME_CAP, targetMinutes = 10, totalRounds = 1)
        val timeCapWorkSnap = TimerSnapshot(
            phase = TimerPhase.WORK,
            mode = TimerMode.TIME_CAP,
            isRunning = true,
            currentRound = 1,
            totalRounds = 1,
            roundSecondsElapsed = 75,
            roundSecondsRemaining = 525,
            roundTotalSeconds = 600,
            activeConfig = timeCapConfig
        )

        assertEquals("01:15", ContinuousTimerUiResolver.formatPrimaryClock(timeCapWorkSnap))
        assertEquals("Cap 10:00 • Rem 08:45", ContinuousTimerUiResolver.formatSecondaryClockHint(timeCapWorkSnap))
        assertFalse(ContinuousTimerUiResolver.shouldConfirmReset(timeCapWorkSnap))
        assertEquals("Skip", ContinuousTimerUiResolver.resolveSkipActionLabel(timeCapWorkSnap))

        val lastRoundUi = ContinuousTimerUiResolver.resolveLiveAdjustmentUiState(timeCapWorkSnap)
        assertTrue(lastRoundUi.showRoundControls)
        assertFalse(lastRoundUi.canDecrementRounds)
        assertFalse(lastRoundUi.canEditNextRound)
        assertEquals("Last round — tap + on Rounds to add R2", lastRoundUi.lastRoundHint)

        // With queued next stage on last round, Skip label becomes "Next Stage" and reset requires confirmation
        val queuedStage = QueuedTimerStage(WorkoutTimerConfig(mode = TimerMode.AMRAP, targetMinutes = 5), transitionSeconds = 60)
        val queuedFinalSnap = timeCapWorkSnap.copy(queuedStages = listOf(queuedStage))
        assertTrue(ContinuousTimerUiResolver.shouldConfirmReset(queuedFinalSnap))
        assertEquals("Next Stage", ContinuousTimerUiResolver.resolveSkipActionLabel(queuedFinalSnap))

        // Transition snapshot -> "Start Now" and confirms reset
        val transitionSnap = queuedFinalSnap.copy(phase = TimerPhase.TRANSITION, roundSecondsRemaining = 45)
        assertTrue(ContinuousTimerUiResolver.shouldConfirmReset(transitionSnap))
        assertEquals("Start Now", ContinuousTimerUiResolver.resolveSkipActionLabel(transitionSnap))

        // Polite accessibility announcements fire ONLY on boundary changes, never on 1Hz ticks
        val boundaryAnnouncement = ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(null, timeCapWorkSnap)
        assertNotNull(boundaryAnnouncement)
        val tick2Snap = timeCapWorkSnap.copy(roundSecondsElapsed = 76, roundSecondsRemaining = 524)
        assertNull(ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(timeCapWorkSnap, tick2Snap))
        val phaseChangeAnnouncement = ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(tick2Snap, transitionSnap)
        assertTrue(phaseChangeAnnouncement?.contains("AMRAP") == true)
    }

    @Test
    fun `group 8 - TimerTeardownController keeps TRANSITION alive and notification formatter & spec customize TRANSITION`() {
        var tornDown = false
        val teardownController = TimerTeardownController(
            onStopForeground = { },
            onDismissNotification = { },
            onReleaseMediaSession = { },
            onStopService = { tornDown = true }
        )
        teardownController.onServiceStarted()

        val queuedAmrap = QueuedTimerStage(
            config = WorkoutTimerConfig(mode = TimerMode.AMRAP, targetMinutes = 8),
            transitionSeconds = 45
        )
        val transitionSnap = TimerSnapshot(
            phase = TimerPhase.TRANSITION,
            isRunning = true,
            currentRound = 10,
            totalRounds = 10,
            roundSecondsRemaining = 45,
            totalSecondsRemaining = 525,
            mode = TimerMode.EMOM,
            queuedStages = listOf(queuedAmrap)
        )

        assertFalse(teardownController.onSnapshotUpdated(transitionSnap))
        assertFalse(tornDown)

        val formatted = TimerNotificationFormatter.createNotificationContent(transitionSnap)
        assertEquals("Transition - Up Next: AMRAP", formatted.title)

        val spec = createTimerNotificationSpec(transitionSnap)
        assertEquals("Transition - Up Next: AMRAP", spec.title)
        assertEquals("Start Now", spec.nextAction.title)

        // Verify WORK phase notification remains unaffected
        val workSnap = transitionSnap.copy(phase = TimerPhase.WORK, currentRound = 3)
        val workSpec = createTimerNotificationSpec(workSnap)
        assertEquals("Work - Round 3/10", workSpec.title)
        assertEquals("Next", workSpec.nextAction.title)
    }

    @Test
    fun `group 9 - 3-block queue progression from Time Cap through 60s transitions and E3MOM stages`() = runTest {
        val engine = createEngine()

        val block1 = WorkoutTimerConfig(
            mode = TimerMode.TIME_CAP,
            targetDurationSeconds = 10,
            totalRounds = 1,
            prepCountdownSeconds = 3,
            workoutLabel = "Block 1 Metcon",
            sourceBlockKey = "b1"
        )
        val block2 = QueuedTimerStage(
            config = WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 5,
                totalRounds = 2,
                prepCountdownSeconds = 0,
                workoutLabel = "Back Squat",
                sourceBlockKey = "b2"
            ),
            transitionSeconds = 60,
            id = "stage-2"
        )
        val block3 = QueuedTimerStage(
            config = WorkoutTimerConfig(
                mode = TimerMode.EMOM,
                intervalSeconds = 4,
                totalRounds = 2,
                prepCountdownSeconds = 0,
                workoutLabel = "Bench Press",
                sourceBlockKey = "b3"
            ),
            transitionSeconds = 60,
            id = "stage-3"
        )

        engine.configureQueue(block1, listOf(block2, block3))
        val idleSnap = engine.snapshot.value
        assertEquals(TimerPhase.IDLE, idleSnap.phase)
        assertEquals(1, idleSnap.currentStageIndex)
        assertEquals(3, idleSnap.totalPlannedStages)
        assertEquals(10 + (60 + 10) + (60 + 8), idleSnap.totalSecondsRemaining)

        // Start -> PREP runs only on Stage 1
        engine.start()
        assertEquals(TimerPhase.PREP, engine.snapshot.value.phase)
        tickSeconds(3)
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals("Block 1 Metcon", engine.snapshot.value.workoutLabel)

        // Finish Time Cap early via skipRound() -> enters 60s TRANSITION into Back Squat
        engine.skipRound()
        val trans1 = engine.snapshot.value
        assertEquals(TimerPhase.TRANSITION, trans1.phase)
        assertEquals(60, trans1.roundSecondsRemaining)
        assertEquals("stage-2", trans1.queuedNextStage?.id)
        assertEquals(2, trans1.queuedStages.size)
        assertEquals(60 + 10 + 60 + 8, trans1.totalSecondsRemaining)

        // Complete 60s transition -> promotes Back Squat directly to WORK (no PREP), retains Bench Press
        tickSeconds(60)
        val stage2Work = engine.snapshot.value
        assertEquals(TimerPhase.WORK, stage2Work.phase)
        assertEquals("Back Squat", stage2Work.workoutLabel)
        assertEquals("b2", stage2Work.activeConfig.sourceBlockKey)
        assertEquals(1, stage2Work.completedStagesCount)
        assertEquals(2, stage2Work.currentStageIndex)
        assertEquals(3, stage2Work.totalPlannedStages)
        assertEquals("stage-3", stage2Work.queuedStages.single().id)

        // Complete Back Squat (2 x 5s) -> enters 60s TRANSITION into Bench Press
        tickSeconds(10)
        val trans2 = engine.snapshot.value
        assertEquals(TimerPhase.TRANSITION, trans2.phase)
        assertEquals(60, trans2.roundSecondsRemaining)
        assertEquals("Bench Press", trans2.queuedNextStage?.config?.workoutLabel)

        // Complete 60s transition -> promotes Bench Press
        tickSeconds(60)
        val stage3Work = engine.snapshot.value
        assertEquals(TimerPhase.WORK, stage3Work.phase)
        assertEquals("Bench Press", stage3Work.workoutLabel)
        assertEquals(2, stage3Work.completedStagesCount)
        assertTrue(stage3Work.queuedStages.isEmpty())

        // Complete Bench Press (2 x 4s) -> FINISHED
        tickSeconds(8)
        assertEquals(TimerPhase.FINISHED, engine.snapshot.value.phase)
        assertEquals(0, engine.snapshot.value.totalSecondsRemaining)

        engine.release()
    }

    @Test
    fun `group 10 - live updateQueuedStage, mid-transition removeQueuedStage, append, replaceTimerQueue, and restart`() = runTest {
        val engine = createEngine()

        val s1 = WorkoutTimerConfig(mode = TimerMode.REST, restSeconds = 5, totalRounds = 1, prepCountdownSeconds = 0, workoutLabel = "Block 1")
        val s2 = QueuedTimerStage(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 10, totalRounds = 2, prepCountdownSeconds = 0, workoutLabel = "Block 2"), transitionSeconds = 60, id = "s2")
        val s3 = QueuedTimerStage(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 10, totalRounds = 2, prepCountdownSeconds = 0, workoutLabel = "Block 3"), transitionSeconds = 60, id = "s3")

        engine.configureQueue(s1, listOf(s2, s3))
        engine.start()

        // Edit Stage 3 live while Stage 1 is running
        engine.updateQueuedStage(
            "s3",
            s3.copy(
                config = WorkoutTimerConfig(mode = TimerMode.AMRAP, targetDurationSeconds = 30, totalRounds = 1, prepCountdownSeconds = 0, workoutLabel = "Block 3"),
                transitionSeconds = 45
            )
        )
        assertEquals(TimerMode.AMRAP, engine.snapshot.value.queuedStages[1].config.mode)
        assertEquals(45, engine.snapshot.value.queuedStages[1].transitionSeconds)

        // Advance into TRANSITION waiting for s2
        tickSeconds(5)
        assertEquals(TimerPhase.TRANSITION, engine.snapshot.value.phase)
        assertEquals("s2", engine.snapshot.value.queuedNextStage?.id)

        // Removing head s2 mid-transition updates Up Next to s3
        engine.removeQueuedStage("s2")
        val afterHeadRemove = engine.snapshot.value
        assertEquals(TimerPhase.TRANSITION, afterHeadRemove.phase)
        assertEquals("s3", afterHeadRemove.queuedNextStage?.id)
        assertEquals(1, afterHeadRemove.queuedStages.size)

        // Removing sole remaining stage s3 mid-transition finishes cleanly
        engine.removeQueuedStage("s3")
        assertEquals(TimerPhase.FINISHED, engine.snapshot.value.phase)
        assertTrue(engine.snapshot.value.queuedStages.isEmpty())

        // replaceTimerQueue replaces atomically without transient IDLE, and restartCurrentStage keeps queue
        val r1 = WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 20, totalRounds = 3, prepCountdownSeconds = 0, workoutLabel = "Rebuilt 1")
        val r2 = QueuedTimerStage(WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 180, totalRounds = 4, prepCountdownSeconds = 0, workoutLabel = "Rebuilt 2"), transitionSeconds = 60, id = "r2")
        val r3 = QueuedTimerStage(WorkoutTimerConfig(mode = TimerMode.REST, restSeconds = 30, totalRounds = 1, prepCountdownSeconds = 0, workoutLabel = "Cooldown"), transitionSeconds = 30, id = "r3")

        engine.replaceTimerQueue(r1, listOf(r2))
        assertEquals(TimerPhase.WORK, engine.snapshot.value.phase)
        assertEquals("Rebuilt 1", engine.snapshot.value.workoutLabel)
        assertEquals(1, engine.snapshot.value.queuedStages.size)

        engine.appendQueuedStage(r3)
        assertEquals(2, engine.snapshot.value.queuedStages.size)

        engine.skipRound() // round 2
        engine.restartCurrentStage()
        assertEquals(1, engine.snapshot.value.currentRound)
        assertEquals(listOf("r2", "r3"), engine.snapshot.value.queuedStages.map { it.id })

        engine.release()
    }

    @Test
    fun `group 11 - ContinuousTimerUiResolver formats block headers, bar subtitles, and block-aware TalkBack`() {
        val s2 = QueuedTimerStage(
            config = WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 180, totalRounds = 4, workoutLabel = "Bench Press"),
            transitionSeconds = 60
        )
        val snap = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 2,
            totalRounds = 4,
            workoutLabel = "Back Squat",
            mode = TimerMode.EMOM,
            activeConfig = WorkoutTimerConfig(mode = TimerMode.EMOM, intervalSeconds = 180, totalRounds = 4, workoutLabel = "Back Squat"),
            queuedStages = listOf(s2),
            completedStagesCount = 0
        )

        assertEquals("Block 1 of 2 • Back Squat", ContinuousTimerUiResolver.formatStagePositionHeader(snap))
        assertEquals("R2/4 • 1/2", ContinuousTimerUiResolver.formatInSessionBarSubtitle(snap))

        val workMsg = ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(null, snap)
        assertTrue(workMsg?.contains("Back Squat") == true)
        assertTrue(workMsg?.contains("block 1 of 2") == true)

        val transSnap = snap.copy(phase = TimerPhase.TRANSITION, roundSecondsRemaining = 45)
        assertEquals("Up Next: Bench Press", ContinuousTimerUiResolver.formatSecondaryClockHint(transSnap))
        assertEquals("Next: Bench Press • 2/2", ContinuousTimerUiResolver.formatInSessionBarSubtitle(transSnap))
        val transMsg = ContinuousTimerUiResolver.resolvePoliteBoundaryAnnouncement(snap, transSnap)
        assertTrue(transMsg?.contains("Bench Press") == true)

        val a11yRow = ContinuousTimerUiResolver.formatQueueRowAccessibilityLabel(1, s2)
        assertTrue(a11yRow.contains("Bench Press"))
        assertTrue(a11yRow.contains("60 seconds transition"))
    }
}
