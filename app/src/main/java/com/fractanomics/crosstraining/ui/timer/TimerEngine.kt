package com.fractanomics.crosstraining.ui.timer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerEngine(
    private val context: Context? = null,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (_: Exception) {
        null
    }

    private val vibrator: Vibrator? = context?.let { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val scope = CoroutineScope(coroutineDispatcher + Job())
    private var timerJob: Job? = null

    private var config = WorkoutTimerConfig()
    val currentConfig: WorkoutTimerConfig get() = config
    private val _snapshot = MutableStateFlow(
        TimerSnapshot(
            totalRounds = config.totalRounds,
            activeConfig = config,
            activeRoundRestSeconds = config.baseRestDurationSeconds()
        )
    )
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    /** Configures an idle/non-running timer stage with an optional single-slot queued next stage. */
    fun configure(newConfig: WorkoutTimerConfig, queuedNext: QueuedTimerStage? = null) {
        configureQueue(newConfig, listOfNotNull(queuedNext))
    }

    /** Configures an idle/non-running timer stage with an ordered multi-stage queue. */
    fun configureQueue(
        newConfig: WorkoutTimerConfig,
        queuedStages: List<QueuedTimerStage> = emptyList()
    ) {
        val currentPhase = _snapshot.value.phase
        if (!_snapshot.value.isRunning &&
            currentPhase != TimerPhase.PREP &&
            currentPhase != TimerPhase.TRANSITION
        ) {
            config = newConfig
            val sanitizedQueue = queuedStages.map {
                it.copy(transitionSeconds = it.transitionSeconds.coerceAtLeast(0))
            }
            resetInternal(queuedStages = sanitizedQueue, resetCompletedStages = true)
        }
    }

    /**
     * Atomically transitions to a new timer configuration without emitting a transient IDLE phase.
     * Cancels any existing timer job and immediately launches the PREP or WORK phase.
     */
    fun replaceTimer(newConfig: WorkoutTimerConfig) {
        replaceTimerQueue(newConfig, emptyList())
    }

    /**
     * Atomically transitions to a new starting configuration and multi-stage queue without
     * emitting a transient IDLE phase.
     */
    fun replaceTimerQueue(
        newConfig: WorkoutTimerConfig,
        queuedStages: List<QueuedTimerStage> = emptyList()
    ) {
        timerJob?.cancel()
        config = newConfig
        val sanitizedQueue = queuedStages.map {
            it.copy(transitionSeconds = it.transitionSeconds.coerceAtLeast(0))
        }
        _snapshot.value = _snapshot.value.copy(
            queuedStages = sanitizedQueue,
            completedStagesCount = 0,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null
        )
        launchConfiguredStage()
    }

    fun start() {
        if (_snapshot.value.isRunning) return

        timerJob?.cancel()
        timerJob = scope.launch {
            if (_snapshot.value.phase == TimerPhase.IDLE || _snapshot.value.phase == TimerPhase.FINISHED) {
                if (config.prepCountdownSeconds > 0) {
                    runPrepPhase()
                } else {
                    startMainTimer()
                }
            } else {
                // Resume paused timer (PREP, WORK, REST, or TRANSITION)
                _snapshot.value = _snapshot.value.copy(isRunning = true)
                runTimerLoop()
            }
        }
    }

    fun pause() {
        timerJob?.cancel()
        _snapshot.value = _snapshot.value.copy(isRunning = false)
    }

    fun reset() {
        resetInternal(queuedStages = emptyList(), resetCompletedStages = true)
    }

    /**
     * Clears queued stages, overrides, completed stage counter, and resets to [TimerPhase.IDLE].
     */
    fun stopAndClearAll() {
        resetInternal(queuedStages = emptyList(), resetCompletedStages = true)
    }

    /**
     * Restarts the active stage from Round 1 while preserving [TimerSnapshot.queuedStages]
     * and [TimerSnapshot.completedStagesCount].
     */
    fun restartCurrentStage() {
        val preservedQueue = _snapshot.value.queuedStages
        val preservedCompleted = _snapshot.value.completedStagesCount
        timerJob?.cancel()
        _snapshot.value = _snapshot.value.copy(
            activeConfig = config,
            queuedStages = preservedQueue,
            completedStagesCount = preservedCompleted,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            totalSecondsElapsed = 0
        )
        launchConfiguredStage()
    }

    /**
     * Launches a new stage directly from [TimerPhase.FINISHED], optionally entering [TimerPhase.TRANSITION] first.
     */
    fun continueFromFinished(nextConfig: WorkoutTimerConfig, transitionSeconds: Int = 0) {
        val current = _snapshot.value
        if (current.phase != TimerPhase.FINISHED) return

        timerJob?.cancel()
        val sanitizedTransition = transitionSeconds.coerceAtLeast(0)
        if (sanitizedTransition > 0) {
            val queued = listOf(QueuedTimerStage(config = nextConfig, transitionSeconds = sanitizedTransition))
            val totalRem = calculateTotalSecondsRemaining(
                config = config,
                phase = TimerPhase.TRANSITION,
                currentRound = current.currentRound,
                totalRounds = current.totalRounds,
                roundSecondsRemaining = sanitizedTransition,
                activeRoundRestSeconds = 0,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                queuedStages = queued
            )
            _snapshot.value = current.copy(
                phase = TimerPhase.TRANSITION,
                isRunning = true,
                roundSecondsRemaining = sanitizedTransition,
                roundSecondsElapsed = 0,
                roundTotalSeconds = sanitizedTransition,
                activeRoundRestSeconds = 0,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                queuedStages = queued,
                totalSecondsRemaining = totalRem
            )
            timerJob = scope.launch { runTimerLoop() }
        } else {
            promoteToConfig(
                nextConfig = nextConfig,
                newQueuedStages = emptyList(),
                incrementCompletedStages = true,
                forceRun = true
            )
        }
    }

    /** Adjusts total rounds by [delta], clamped to >= currentRound, purging stale next-round overrides if shrunk. */
    fun adjustTotalRounds(delta: Int) {
        val current = _snapshot.value
        if (current.phase == TimerPhase.TRANSITION || current.phase == TimerPhase.FINISHED) return
        val baseTotal = if (current.phase == TimerPhase.IDLE) config.totalRounds else current.totalRounds
        setTotalRounds(baseTotal + delta)
    }

    /** Sets total rounds to [newTotal], clamped to >= currentRound, purging next-round overrides if shrunk to currentRound. */
    fun setTotalRounds(newTotal: Int) {
        val current = _snapshot.value
        if (current.phase == TimerPhase.TRANSITION || current.phase == TimerPhase.FINISHED) return

        val minAllowed = if (current.phase == TimerPhase.IDLE) 1 else current.currentRound.coerceAtLeast(1)
        val clampedTotal = newTotal.coerceIn(minAllowed, 999)
        config = config.copy(totalRounds = clampedTotal)

        val keepOverrides = clampedTotal > current.currentRound && current.phase != TimerPhase.IDLE
        val nextWorkOverride = if (keepOverrides) current.nextRoundOverrideSeconds else null
        val nextRestOverride = if (keepOverrides) current.nextRoundRestOverrideSeconds else null

        val recalculatedTotal = if (current.phase == TimerPhase.IDLE) {
            calculateTotalSecondsRemaining(
                config = config,
                phase = TimerPhase.IDLE,
                currentRound = 1,
                totalRounds = clampedTotal,
                roundSecondsRemaining = config.baseWorkDurationSeconds(),
                activeRoundRestSeconds = config.baseRestDurationSeconds(),
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                queuedStages = current.queuedStages
            )
        } else {
            calculateTotalSecondsRemaining(
                config = config,
                phase = current.phase,
                currentRound = current.currentRound,
                totalRounds = clampedTotal,
                roundSecondsRemaining = current.roundSecondsRemaining,
                activeRoundRestSeconds = current.activeRoundRestSeconds,
                nextRoundOverrideSeconds = nextWorkOverride,
                nextRoundRestOverrideSeconds = nextRestOverride,
                queuedStages = current.queuedStages
            )
        }

        _snapshot.value = current.copy(
            totalRounds = clampedTotal,
            activeConfig = config,
            nextRoundOverrideSeconds = nextWorkOverride,
            nextRoundRestOverrideSeconds = nextRestOverride,
            totalSecondsRemaining = recalculatedTotal
        )
    }

    /** Adjusts the single-round work/duration override for round `currentRound + 1`. */
    fun adjustNextRoundDuration(deltaSeconds: Int) {
        val current = _snapshot.value
        if (!canEditNextRound(current)) return
        val baseline = current.nextRoundOverrideSeconds ?: config.baseWorkDurationSeconds().coerceAtLeast(5)
        setNextRoundDuration(baseline + deltaSeconds)
    }

    /** Sets a single-round work/duration override for round `currentRound + 1`, clamped `>= 5s`. */
    fun setNextRoundDuration(newSeconds: Int) {
        val current = _snapshot.value
        if (!canEditNextRound(current)) return
        val clamped = newSeconds.coerceIn(5, 86400)
        val effectiveOverride = if (clamped == config.baseWorkDurationSeconds()) null else clamped
        updateOverrides(effectiveOverride, current.nextRoundRestOverrideSeconds)
    }

    /** Adjusts the single-round Tabata rest override for round `currentRound + 1`. */
    fun adjustNextRoundRestDuration(deltaSeconds: Int) {
        val current = _snapshot.value
        if (!canEditNextRound(current) || config.mode != TimerMode.TABATA) return
        val baseline = current.nextRoundRestOverrideSeconds ?: config.baseRestDurationSeconds()
        setNextRoundRestDuration(baseline + deltaSeconds)
    }

    /** Sets a single-round Tabata rest override for round `currentRound + 1`, clamped `>= 0s`. */
    fun setNextRoundRestDuration(newSeconds: Int) {
        val current = _snapshot.value
        if (!canEditNextRound(current) || config.mode != TimerMode.TABATA) return
        val clamped = newSeconds.coerceIn(0, 3600)
        val effectiveOverride = if (clamped == config.baseRestDurationSeconds()) null else clamped
        updateOverrides(current.nextRoundOverrideSeconds, effectiveOverride)
    }

    /** Clears pending single-round overrides for `currentRound + 1`. */
    fun clearNextRoundOverrides() {
        val current = _snapshot.value
        if (current.nextRoundOverrideSeconds == null && current.nextRoundRestOverrideSeconds == null) return
        updateOverrides(nextWork = null, nextRest = null)
    }

    /** Replaces the full upcoming stage queue in any phase, finishing cleanly if removed mid-transition. */
    fun setQueuedStages(stages: List<QueuedTimerStage>) {
        val current = _snapshot.value
        val sanitized = stages.map {
            it.copy(transitionSeconds = it.transitionSeconds.coerceAtLeast(0))
        }
        if (current.phase == TimerPhase.TRANSITION && sanitized.isEmpty()) {
            _snapshot.value = current.copy(queuedStages = emptyList())
            finishTimer(totalElapsedOverride = current.totalSecondsElapsed)
            return
        }
        val recalculatedTotal = calculateTotalSecondsRemaining(
            config = config,
            phase = current.phase,
            currentRound = current.currentRound,
            totalRounds = current.totalRounds,
            roundSecondsRemaining = current.roundSecondsRemaining,
            activeRoundRestSeconds = current.activeRoundRestSeconds,
            nextRoundOverrideSeconds = current.nextRoundOverrideSeconds,
            nextRoundRestOverrideSeconds = current.nextRoundRestOverrideSeconds,
            queuedStages = sanitized
        )
        _snapshot.value = current.copy(
            queuedStages = sanitized,
            totalSecondsRemaining = recalculatedTotal
        )
    }

    /** Updates a specific queued stage by [stageId] in place, syncing active transition countdown if head. */
    fun updateQueuedStage(stageId: String, updated: QueuedTimerStage) {
        val current = _snapshot.value
        val idx = current.queuedStages.indexOfFirst { it.id == stageId }
        if (idx < 0) return

        val sanitizedStage = updated.copy(
            id = stageId,
            transitionSeconds = updated.transitionSeconds.coerceAtLeast(0)
        )
        val updatedList = current.queuedStages.toMutableList().apply {
            set(idx, sanitizedStage)
        }.toList()

        if (current.phase == TimerPhase.TRANSITION && idx == 0) {
            if (sanitizedStage.transitionSeconds <= 0) {
                _snapshot.value = current.copy(queuedStages = updatedList)
                promoteQueuedNextStage(forceRun = current.isRunning)
                return
            }
            val previousTransition = current.queuedStages.first().transitionSeconds
            if (sanitizedStage.transitionSeconds != previousTransition) {
                val newRem = sanitizedStage.transitionSeconds.coerceIn(1, 3600)
                val totalRem = calculateTotalSecondsRemaining(
                    config = config,
                    phase = TimerPhase.TRANSITION,
                    currentRound = current.currentRound,
                    totalRounds = current.totalRounds,
                    roundSecondsRemaining = newRem,
                    activeRoundRestSeconds = 0,
                    nextRoundOverrideSeconds = null,
                    nextRoundRestOverrideSeconds = null,
                    queuedStages = updatedList
                )
                _snapshot.value = current.copy(
                    roundSecondsRemaining = newRem,
                    roundTotalSeconds = maxOf(current.roundTotalSeconds, newRem),
                    queuedStages = updatedList,
                    totalSecondsRemaining = totalRem
                )
                return
            }
        }
        setQueuedStages(updatedList)
    }

    /** Removes a queued stage by [stageId], finishing cleanly if the sole head stage is removed mid-transition. */
    fun removeQueuedStage(stageId: String) {
        setQueuedStages(_snapshot.value.queuedStages.filterNot { it.id == stageId })
    }

    /** Appends a new stage to the tail of [TimerSnapshot.queuedStages]. */
    fun appendQueuedStage(stage: QueuedTimerStage) {
        val sanitized = stage.copy(transitionSeconds = stage.transitionSeconds.coerceAtLeast(0))
        setQueuedStages(_snapshot.value.queuedStages + sanitized)
    }

    /** Sets or clears the head stage (`queuedNextStage`) while preserving any trailing queued stages. */
    fun setQueuedNextStage(nextStage: QueuedTimerStage?) {
        val currentQueue = _snapshot.value.queuedStages
        val updatedQueue = if (nextStage == null) {
            currentQueue.drop(1)
        } else {
            val preservedId = currentQueue.firstOrNull()?.id ?: nextStage.id
            listOf(nextStage.copy(id = preservedId)) + currentQueue.drop(1)
        }
        setQueuedStages(updatedQueue)
    }

    /** Updates the config of [TimerSnapshot.queuedNextStage] (or initializes with 60s transition if absent). */
    fun updateQueuedNextConfig(nextConfig: WorkoutTimerConfig) {
        val existingHead = _snapshot.value.queuedStages.firstOrNull()
        val updatedHead = if (existingHead != null) {
            existingHead.copy(config = nextConfig)
        } else {
            QueuedTimerStage(config = nextConfig, transitionSeconds = 60)
        }
        setQueuedNextStage(updatedHead)
    }

    /** Adjusts remaining countdown seconds while in [TimerPhase.TRANSITION]. */
    fun adjustTransitionRemaining(deltaSeconds: Int) {
        val current = _snapshot.value
        if (current.phase != TimerPhase.TRANSITION) return
        setTransitionRemaining(current.roundSecondsRemaining + deltaSeconds)
    }

    /** Sets exact remaining countdown seconds during [TimerPhase.TRANSITION], promoting immediately at `0s`. */
    fun setTransitionRemaining(newSeconds: Int) {
        val current = _snapshot.value
        if (current.phase != TimerPhase.TRANSITION) return
        val clamped = newSeconds.coerceIn(0, 3600)
        if (clamped == 0) {
            if (current.queuedNextStage != null) {
                promoteQueuedNextStage(forceRun = current.isRunning)
            } else {
                finishTimer()
            }
            return
        }
        val updatedQueue = if (current.queuedStages.isNotEmpty()) {
            listOf(current.queuedStages.first().copy(transitionSeconds = clamped)) + current.queuedStages.drop(1)
        } else {
            emptyList()
        }
        val totalRem = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.TRANSITION,
            currentRound = current.currentRound,
            totalRounds = current.totalRounds,
            roundSecondsRemaining = clamped,
            activeRoundRestSeconds = 0,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = updatedQueue
        )
        _snapshot.value = current.copy(
            roundSecondsRemaining = clamped,
            roundTotalSeconds = maxOf(current.roundTotalSeconds, clamped),
            queuedStages = updatedQueue,
            totalSecondsRemaining = totalRem
        )
    }

    fun skipRound() {
        val current = _snapshot.value
        if (!current.isRunning &&
            current.phase != TimerPhase.WORK &&
            current.phase != TimerPhase.REST &&
            current.phase != TimerPhase.PREP &&
            current.phase != TimerPhase.TRANSITION
        ) return

        val wasRunning = current.isRunning
        if (current.phase == TimerPhase.TRANSITION) {
            promoteQueuedNextStage(forceRun = true)
            return
        }

        timerJob?.cancel()
        advanceToNextPhaseOrRound(fromLoop = false)
        if (_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.FINISHED && !wasRunning) {
            timerJob = scope.launch { runTimerLoop() }
        } else if (wasRunning && _snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.FINISHED) {
            timerJob = scope.launch { runTimerLoop() }
        }
    }

    /**
     * Rewinds the active timer to the previous round or phase.
     *
     * - If in [TimerPhase.FINISHED], rewinds to the final work round in a clean paused state (`isRunning = false`)
     *   for multi-round sessions (and EMOM/TABATA/DEATH_BY), keeping single-round AMRAP/TIME_CAP/REST a strict no-op.
     * - If in [TimerPhase.IDLE], [TimerPhase.PREP], or [TimerPhase.TRANSITION], this is a strict no-op.
     * - For [TimerMode.TABATA] in [TimerPhase.REST], reverts to [TimerPhase.WORK] of the same round.
     * - Otherwise decrements `currentRound` if `> 1` and restores base duration for that round.
     */
    fun previousRound() {
        val current = _snapshot.value
        if (current.phase == TimerPhase.IDLE ||
            current.phase == TimerPhase.PREP ||
            current.phase == TimerPhase.TRANSITION
        ) return

        if (current.phase == TimerPhase.FINISHED) {
            if (current.totalRounds <= 1 &&
                config.mode in setOf(TimerMode.AMRAP, TimerMode.TIME_CAP, TimerMode.REST)
            ) {
                return
            }
            timerJob?.cancel()
            val finalRound = current.totalRounds.coerceAtLeast(1)
            val roundSecs = config.baseWorkDurationSeconds()
            val restSecs = config.baseRestDurationSeconds()
            val remainingTotal = calculateTotalSecondsRemaining(
                config = config,
                phase = TimerPhase.WORK,
                currentRound = finalRound,
                totalRounds = finalRound,
                roundSecondsRemaining = roundSecs,
                activeRoundRestSeconds = restSecs,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                queuedStages = current.queuedStages
            )
            _snapshot.value = current.copy(
                phase = TimerPhase.WORK,
                isRunning = false,
                currentRound = finalRound,
                roundSecondsRemaining = roundSecs,
                roundSecondsElapsed = 0,
                roundTotalSeconds = roundSecs,
                activeRoundRestSeconds = restSecs,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                targetRepsCurrentRound = if (config.mode == TimerMode.DEATH_BY) finalRound else 1,
                totalSecondsRemaining = remainingTotal
            )
            return
        }

        if (config.mode == TimerMode.TABATA && current.phase == TimerPhase.REST) {
            val workSecs = config.baseWorkDurationSeconds()
            val restSecs = current.activeRoundRestSeconds.coerceAtLeast(0)
            val remainingTotal = calculateTotalSecondsRemaining(
                config = config,
                phase = TimerPhase.WORK,
                currentRound = current.currentRound,
                totalRounds = current.totalRounds,
                roundSecondsRemaining = workSecs,
                activeRoundRestSeconds = restSecs,
                nextRoundOverrideSeconds = current.nextRoundOverrideSeconds,
                nextRoundRestOverrideSeconds = current.nextRoundRestOverrideSeconds,
                queuedStages = current.queuedStages
            )
            _snapshot.value = current.copy(
                phase = TimerPhase.WORK,
                roundSecondsRemaining = workSecs,
                roundSecondsElapsed = 0,
                roundTotalSeconds = workSecs,
                totalSecondsRemaining = remainingTotal
            )
            return
        }

        if (current.totalRounds <= 1 &&
            config.mode in setOf(TimerMode.AMRAP, TimerMode.TIME_CAP, TimerMode.REST)
        ) {
            return
        }

        if (current.currentRound <= 1) return

        val prevRound = current.currentRound - 1
        val roundSecs = config.baseWorkDurationSeconds()
        val restSecs = config.baseRestDurationSeconds()
        val remainingTotal = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.WORK,
            currentRound = prevRound,
            totalRounds = current.totalRounds,
            roundSecondsRemaining = roundSecs,
            activeRoundRestSeconds = restSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = current.queuedStages
        )
        _snapshot.value = current.copy(
            phase = TimerPhase.WORK,
            currentRound = prevRound,
            roundSecondsRemaining = roundSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = roundSecs,
            activeRoundRestSeconds = restSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            targetRepsCurrentRound = if (config.mode == TimerMode.DEATH_BY) prevRound else 1,
            totalSecondsRemaining = remainingTotal
        )
    }

    private fun resetInternal(queuedStages: List<QueuedTimerStage>, resetCompletedStages: Boolean) {
        timerJob?.cancel()
        val totalSecs = calculateInitialTotalSeconds(config, queuedStages)
        val roundSecs = calculateInitialRoundSeconds(config)
        val completedCount = if (resetCompletedStages) 0 else _snapshot.value.completedStagesCount

        _snapshot.value = TimerSnapshot(
            phase = TimerPhase.IDLE,
            isRunning = false,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = roundSecs,
            totalSecondsElapsed = 0,
            totalSecondsRemaining = totalSecs,
            targetRepsCurrentRound = 1,
            workoutLabel = config.workoutLabel,
            mode = config.mode,
            activeConfig = config,
            activeRoundRestSeconds = config.baseRestDurationSeconds(),
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = queuedStages,
            completedStagesCount = completedCount
        )
    }

    private fun launchConfiguredStage() {
        timerJob?.cancel()
        if (config.prepCountdownSeconds > 0) {
            timerJob = scope.launch { runPrepPhase() }
        } else {
            timerJob = scope.launch { startMainTimer() }
        }
    }

    private fun canEditNextRound(current: TimerSnapshot): Boolean =
        current.phase in setOf(TimerPhase.PREP, TimerPhase.WORK, TimerPhase.REST) &&
            current.currentRound < current.totalRounds

    private fun updateOverrides(nextWork: Int?, nextRest: Int?) {
        val current = _snapshot.value
        val recalculatedTotal = calculateTotalSecondsRemaining(
            config = config,
            phase = current.phase,
            currentRound = current.currentRound,
            totalRounds = current.totalRounds,
            roundSecondsRemaining = current.roundSecondsRemaining,
            activeRoundRestSeconds = current.activeRoundRestSeconds,
            nextRoundOverrideSeconds = nextWork,
            nextRoundRestOverrideSeconds = nextRest,
            queuedStages = current.queuedStages
        )
        _snapshot.value = current.copy(
            nextRoundOverrideSeconds = nextWork,
            nextRoundRestOverrideSeconds = nextRest,
            totalSecondsRemaining = recalculatedTotal
        )
    }

    private suspend fun runPrepPhase() {
        var prepLeft = config.prepCountdownSeconds
        val current = _snapshot.value
        val totalSecs = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.PREP,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = config.baseWorkDurationSeconds(),
            activeRoundRestSeconds = config.baseRestDurationSeconds(),
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = current.queuedStages
        )
        _snapshot.value = current.copy(
            phase = TimerPhase.PREP,
            isRunning = true,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = prepLeft,
            roundSecondsElapsed = 0,
            roundTotalSeconds = config.prepCountdownSeconds,
            totalSecondsElapsed = 0,
            totalSecondsRemaining = totalSecs,
            targetRepsCurrentRound = 1,
            workoutLabel = config.workoutLabel,
            mode = config.mode,
            activeConfig = config,
            activeRoundRestSeconds = config.baseRestDurationSeconds(),
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null
        )

        while (prepLeft > 0) {
            if (prepLeft in 1..3 && config.soundEnabled) playBeep(high = false)
            if (prepLeft in 1..3 && config.vibrationEnabled) vibrateShort()

            delay(1000)
            prepLeft--
            _snapshot.value = _snapshot.value.copy(
                roundSecondsRemaining = prepLeft,
                roundSecondsElapsed = config.prepCountdownSeconds - prepLeft
            )
        }

        if (config.soundEnabled) playBeep(high = true)
        if (config.vibrationEnabled) vibrateLong()
        startMainTimer()
    }

    private suspend fun startMainTimer() {
        enterWorkRound1(totalElapsedBase = 0, isRunning = true)
        runTimerLoop()
    }

    private fun enterWorkRound1(totalElapsedBase: Int, isRunning: Boolean) {
        val current = _snapshot.value
        val roundSecs = calculateInitialRoundSeconds(config)
        val restSecs = config.baseRestDurationSeconds()
        val totalSecs = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.WORK,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            activeRoundRestSeconds = restSecs,
            nextRoundOverrideSeconds = current.nextRoundOverrideSeconds,
            nextRoundRestOverrideSeconds = current.nextRoundRestOverrideSeconds,
            queuedStages = current.queuedStages
        )

        _snapshot.value = current.copy(
            phase = TimerPhase.WORK,
            isRunning = isRunning,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = roundSecs,
            totalSecondsElapsed = totalElapsedBase,
            totalSecondsRemaining = totalSecs,
            targetRepsCurrentRound = 1,
            workoutLabel = config.workoutLabel,
            mode = config.mode,
            activeConfig = config,
            activeRoundRestSeconds = restSecs
        )
    }

    private suspend fun runTimerLoop() {
        while (_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.FINISHED) {
            delay(1000)
            val current = _snapshot.value

            val newRoundRemaining = current.roundSecondsRemaining - 1
            val newRoundElapsed = current.roundSecondsElapsed + 1
            val newTotalElapsed = current.totalSecondsElapsed + 1
            val newTotalRemaining = (current.totalSecondsRemaining - 1).coerceAtLeast(0)
            val sound = current.activeConfig.soundEnabled
            val vibrate = current.activeConfig.vibrationEnabled

            if (newRoundRemaining in 1..3 && sound) {
                playBeep(high = false)
            }
            if (newRoundRemaining in 1..3 && vibrate) {
                vibrateShort()
            }

            if (newRoundRemaining <= 0) {
                if (sound) playBeep(high = true)
                if (vibrate) vibrateLong()
                advanceToNextPhaseOrRound(
                    fromLoop = true,
                    totalElapsedOverride = newTotalElapsed
                )
            } else {
                _snapshot.value = current.copy(
                    roundSecondsRemaining = newRoundRemaining,
                    roundSecondsElapsed = newRoundElapsed,
                    totalSecondsElapsed = newTotalElapsed,
                    totalSecondsRemaining = newTotalRemaining
                )
            }
        }
    }

    private fun advanceToNextPhaseOrRound(
        fromLoop: Boolean = false,
        totalElapsedOverride: Int? = null
    ) {
        val current = _snapshot.value
        val elapsedToCarry = totalElapsedOverride ?: current.totalSecondsElapsed

        if (current.phase == TimerPhase.PREP) {
            if (!fromLoop) timerJob?.cancel()
            enterWorkRound1(totalElapsedBase = 0, isRunning = current.isRunning)
            return
        }

        if (current.phase == TimerPhase.TRANSITION) {
            if (current.queuedNextStage != null) {
                promoteQueuedNextStage(forceRun = true, totalElapsedBase = elapsedToCarry)
            } else {
                finishTimer(totalElapsedOverride = elapsedToCarry)
            }
            return
        }

        if (config.mode == TimerMode.TABATA && current.phase == TimerPhase.WORK) {
            val restSecs = current.activeRoundRestSeconds.coerceAtLeast(0)
            if (restSecs > 0) {
                val remainingTotal = calculateTotalSecondsRemaining(
                    config = config,
                    phase = TimerPhase.REST,
                    currentRound = current.currentRound,
                    totalRounds = current.totalRounds,
                    roundSecondsRemaining = restSecs,
                    activeRoundRestSeconds = 0,
                    nextRoundOverrideSeconds = current.nextRoundOverrideSeconds,
                    nextRoundRestOverrideSeconds = current.nextRoundRestOverrideSeconds,
                    queuedStages = current.queuedStages
                )
                _snapshot.value = current.copy(
                    phase = TimerPhase.REST,
                    roundSecondsRemaining = restSecs,
                    roundSecondsElapsed = 0,
                    roundTotalSeconds = restSecs,
                    totalSecondsElapsed = elapsedToCarry,
                    totalSecondsRemaining = remainingTotal
                )
                return
            }
        }

        if (current.currentRound < current.totalRounds) {
            advanceToNextWorkRound(current, elapsedToCarry)
        } else {
            handleStageEnd(current, elapsedToCarry)
        }
    }

    private fun advanceToNextWorkRound(current: TimerSnapshot, elapsedToCarry: Int) {
        val nextRound = current.currentRound + 1
        val nextWorkSecs = (current.nextRoundOverrideSeconds ?: config.baseWorkDurationSeconds()).coerceAtLeast(1)
        val nextRestSecs = (current.nextRoundRestOverrideSeconds ?: config.baseRestDurationSeconds()).coerceAtLeast(0)
        val nextTargetReps = if (config.mode == TimerMode.DEATH_BY) nextRound else 1
        val totalRem = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.WORK,
            currentRound = nextRound,
            totalRounds = current.totalRounds,
            roundSecondsRemaining = nextWorkSecs,
            activeRoundRestSeconds = nextRestSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = current.queuedStages
        )
        _snapshot.value = current.copy(
            phase = TimerPhase.WORK,
            currentRound = nextRound,
            roundSecondsRemaining = nextWorkSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = nextWorkSecs,
            activeRoundRestSeconds = nextRestSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            targetRepsCurrentRound = nextTargetReps,
            totalSecondsElapsed = elapsedToCarry,
            totalSecondsRemaining = totalRem
        )
    }

    private fun handleStageEnd(current: TimerSnapshot, elapsedToCarry: Int) {
        val queued = current.queuedNextStage
        if (queued == null) {
            finishTimer(totalElapsedOverride = elapsedToCarry)
            return
        }

        if (queued.transitionSeconds > 0) {
            val tSecs = queued.transitionSeconds.coerceAtLeast(1)
            val totalRem = calculateTotalSecondsRemaining(
                config = config,
                phase = TimerPhase.TRANSITION,
                currentRound = current.currentRound,
                totalRounds = current.totalRounds,
                roundSecondsRemaining = tSecs,
                activeRoundRestSeconds = 0,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                queuedStages = current.queuedStages
            )
            _snapshot.value = current.copy(
                phase = TimerPhase.TRANSITION,
                roundSecondsRemaining = tSecs,
                roundSecondsElapsed = 0,
                roundTotalSeconds = tSecs,
                activeRoundRestSeconds = 0,
                nextRoundOverrideSeconds = null,
                nextRoundRestOverrideSeconds = null,
                totalSecondsElapsed = elapsedToCarry,
                totalSecondsRemaining = totalRem
            )
        } else {
            promoteQueuedNextStage(forceRun = true, totalElapsedBase = elapsedToCarry)
        }
    }

    private fun promoteQueuedNextStage(
        forceRun: Boolean,
        totalElapsedBase: Int = _snapshot.value.totalSecondsElapsed
    ) {
        val currentQueue = _snapshot.value.queuedStages
        val head = currentQueue.firstOrNull() ?: return
        promoteToConfig(
            nextConfig = head.config,
            newQueuedStages = currentQueue.drop(1),
            incrementCompletedStages = true,
            forceRun = forceRun,
            totalElapsedBase = totalElapsedBase
        )
    }

    private fun promoteToConfig(
        nextConfig: WorkoutTimerConfig,
        newQueuedStages: List<QueuedTimerStage>,
        incrementCompletedStages: Boolean,
        forceRun: Boolean,
        totalElapsedBase: Int = _snapshot.value.totalSecondsElapsed
    ) {
        val current = _snapshot.value
        timerJob?.cancel()
        config = nextConfig
        val nextCompletedCount = if (incrementCompletedStages) current.completedStagesCount + 1 else current.completedStagesCount
        val roundSecs = calculateInitialRoundSeconds(config)
        val restSecs = config.baseRestDurationSeconds()
        val totalRem = calculateTotalSecondsRemaining(
            config = config,
            phase = TimerPhase.WORK,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            activeRoundRestSeconds = restSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = newQueuedStages
        )

        _snapshot.value = TimerSnapshot(
            phase = TimerPhase.WORK,
            isRunning = forceRun,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = roundSecs,
            totalSecondsElapsed = totalElapsedBase,
            totalSecondsRemaining = totalRem,
            targetRepsCurrentRound = 1,
            workoutLabel = config.workoutLabel,
            mode = config.mode,
            activeConfig = config,
            activeRoundRestSeconds = restSecs,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null,
            queuedStages = newQueuedStages,
            completedStagesCount = nextCompletedCount
        )

        if (config.soundEnabled) playBeep(high = true)
        if (config.vibrationEnabled) vibrateLong()
        if (forceRun) {
            timerJob = scope.launch { runTimerLoop() }
        }
    }

    private fun finishTimer(totalElapsedOverride: Int? = null) {
        timerJob?.cancel()
        val current = _snapshot.value
        _snapshot.value = current.copy(
            phase = TimerPhase.FINISHED,
            isRunning = false,
            roundSecondsRemaining = 0,
            totalSecondsElapsed = totalElapsedOverride ?: current.totalSecondsElapsed,
            totalSecondsRemaining = 0,
            nextRoundOverrideSeconds = null,
            nextRoundRestOverrideSeconds = null
        )
        if (config.soundEnabled) {
            playBeep(high = true)
        }
        if (config.vibrationEnabled) {
            vibrateLong()
        }
    }

    private fun calculateInitialTotalSeconds(
        cfg: WorkoutTimerConfig,
        queuedStages: List<QueuedTimerStage> = emptyList()
    ): Int {
        val queuedSeconds = queuedStages.sumOf {
            it.transitionSeconds.coerceAtLeast(0) + it.config.stageTotalSeconds()
        }
        return cfg.stageTotalSeconds() + queuedSeconds
    }

    private fun calculateInitialRoundSeconds(cfg: WorkoutTimerConfig): Int = cfg.baseWorkDurationSeconds()

    private fun playBeep(high: Boolean) {
        try {
            val type = if (high) ToneGenerator.TONE_CDMA_HIGH_L else ToneGenerator.TONE_PROP_BEEP
            val duration = if (high) 400 else 200
            toneGenerator?.startTone(type, duration)
        } catch (_: Exception) {}
    }

    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateLong() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        reset()
    }

    fun release() {
        timerJob?.cancel()
        toneGenerator?.release()
    }

    companion object {
        fun getInstance(context: Context): TimerEngine = TimerEngineProvider.get(context)

        /**
         * Backward-compatible overload computing remaining seconds with an optional single [queuedNextStage].
         */
        fun calculateTotalSecondsRemaining(
            config: WorkoutTimerConfig,
            phase: TimerPhase,
            currentRound: Int,
            totalRounds: Int,
            roundSecondsRemaining: Int,
            activeRoundRestSeconds: Int,
            nextRoundOverrideSeconds: Int?,
            nextRoundRestOverrideSeconds: Int?,
            queuedNextStage: QueuedTimerStage?
        ): Int = calculateTotalSecondsRemaining(
            config = config,
            phase = phase,
            currentRound = currentRound,
            totalRounds = totalRounds,
            roundSecondsRemaining = roundSecondsRemaining,
            activeRoundRestSeconds = activeRoundRestSeconds,
            nextRoundOverrideSeconds = nextRoundOverrideSeconds,
            nextRoundRestOverrideSeconds = nextRoundRestOverrideSeconds,
            queuedStages = listOfNotNull(queuedNextStage)
        )

        /**
         * Deterministically computes total seconds remaining across active round, single-round override (`R{c+1}`),
         * remaining unmodified rounds (`R{c+2}..totalRounds`), and all [queuedStages] + transition countdowns.
         * Excludes [WorkoutTimerConfig.prepCountdownSeconds] during [TimerPhase.PREP] / [TimerPhase.IDLE] for
         * backward compatibility with workout-clock assertions.
         */
        fun calculateTotalSecondsRemaining(
            config: WorkoutTimerConfig,
            phase: TimerPhase,
            currentRound: Int,
            totalRounds: Int,
            roundSecondsRemaining: Int,
            activeRoundRestSeconds: Int,
            nextRoundOverrideSeconds: Int?,
            nextRoundRestOverrideSeconds: Int?,
            queuedStages: List<QueuedTimerStage>
        ): Int {
            if (phase == TimerPhase.FINISHED) return 0

            if (phase == TimerPhase.TRANSITION) {
                val headWorkOnly = queuedStages.firstOrNull()?.config?.stageTotalSeconds() ?: 0
                val tailTotal = queuedStages.drop(1).sumOf {
                    it.transitionSeconds.coerceAtLeast(0) + it.config.stageTotalSeconds()
                }
                return roundSecondsRemaining.coerceAtLeast(0) + headWorkOnly + tailTotal
            }

            val queuedSeconds = queuedStages.sumOf {
                it.transitionSeconds.coerceAtLeast(0) + it.config.stageTotalSeconds()
            }

            val safeRounds = totalRounds.coerceAtLeast(1)
            val safeCurrent = currentRound.coerceIn(1, safeRounds)

            fun futureAfter(roundIndex: Int): Int {
                if (roundIndex >= safeRounds) return 0
                val nextWork = (nextRoundOverrideSeconds ?: config.baseWorkDurationSeconds()).coerceAtLeast(0)
                val nextRest = if (config.mode == TimerMode.TABATA) {
                    (nextRoundRestOverrideSeconds ?: config.baseRestDurationSeconds()).coerceAtLeast(0)
                } else {
                    0
                }
                val afterNextCount = (safeRounds - (roundIndex + 1)).coerceAtLeast(0)
                return nextWork + nextRest + afterNextCount * config.singleRoundTotalSeconds()
            }

            val activeStageRemaining = when (phase) {
                TimerPhase.IDLE, TimerPhase.PREP -> {
                    config.singleRoundTotalSeconds() + futureAfter(1)
                }
                TimerPhase.WORK -> {
                    val currentRest = if (config.mode == TimerMode.TABATA) {
                        activeRoundRestSeconds.coerceAtLeast(0)
                    } else {
                        0
                    }
                    roundSecondsRemaining.coerceAtLeast(0) + currentRest + futureAfter(safeCurrent)
                }
                TimerPhase.REST -> {
                    roundSecondsRemaining.coerceAtLeast(0) + futureAfter(safeCurrent)
                }
                TimerPhase.TRANSITION, TimerPhase.FINISHED -> 0
            }

            return (activeStageRemaining + queuedSeconds).coerceAtLeast(0)
        }
    }
}
