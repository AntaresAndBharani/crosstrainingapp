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
    private val _snapshot = MutableStateFlow(TimerSnapshot())
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    fun configure(newConfig: WorkoutTimerConfig) {
        if (!_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.PREP) {
            config = newConfig
            reset()
        }
    }

    /**
     * Atomically transitions to a new timer configuration without emitting a transient IDLE phase.
     * Cancels any existing timer job and immediately launches the PREP or WORK phase.
     */
    fun replaceTimer(newConfig: WorkoutTimerConfig) {
        timerJob?.cancel()
        config = newConfig
        val totalSecs = calculateInitialTotalSeconds(config)
        val roundSecs = calculateInitialRoundSeconds(config)

        if (config.prepCountdownSeconds > 0) {
            timerJob = scope.launch {
                runPrepPhase()
            }
        } else {
            timerJob = scope.launch {
                startMainTimer()
            }
        }
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
                // Resume paused timer
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
        timerJob?.cancel()
        val totalSecs = calculateInitialTotalSeconds(config)
        val roundSecs = calculateInitialRoundSeconds(config)

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
            mode = config.mode
        )
    }

    fun skipRound() {
        if (!_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.WORK && _snapshot.value.phase != TimerPhase.REST && _snapshot.value.phase != TimerPhase.PREP) return
        advanceToNextPhaseOrRound()
    }

    /**
     * Rewinds the active timer to the previous round or phase.
     *
     * - If in [TimerPhase.FINISHED], rewinds to the final work round in a clean paused state (isRunning = false).
     * - For [TimerMode.EMOM]: if currentRound > 1, decrements round and resets round clock to intervalSeconds.
     * - For [TimerMode.TABATA]: if in REST, reverts to WORK of the same round; if in WORK and currentRound > 1, decrements round to WORK.
     * - For [TimerMode.DEATH_BY]: if currentRound > 1, decrements round, resets round clock to 60s, and updates targetRepsCurrentRound.
     * - For single-round modes ([TimerMode.AMRAP], [TimerMode.TIME_CAP], [TimerMode.REST]) or boundary conditions (currentRound <= 1 in WORK, or IDLE/PREP phase), this is a strict no-op.
     */
    fun previousRound() {
        val current = _snapshot.value
        if (current.phase == TimerPhase.IDLE || current.phase == TimerPhase.PREP) return

        if (current.phase == TimerPhase.FINISHED) {
            timerJob?.cancel()
            val finalRound = config.totalRounds
            when (config.mode) {
                TimerMode.EMOM -> {
                    val roundSecs = config.intervalSeconds
                    val remainingTotal = roundSecs
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        isRunning = false,
                        currentRound = finalRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        totalSecondsRemaining = remainingTotal
                    )
                }
                TimerMode.TABATA -> {
                    val roundSecs = config.workSeconds
                    val remainingTotal = config.workSeconds + config.restSeconds
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        isRunning = false,
                        currentRound = finalRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        totalSecondsRemaining = remainingTotal
                    )
                }
                TimerMode.DEATH_BY -> {
                    val roundSecs = 60
                    val remainingTotal = 60
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        isRunning = false,
                        currentRound = finalRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        targetRepsCurrentRound = finalRound,
                        totalSecondsRemaining = remainingTotal
                    )
                }
                TimerMode.AMRAP, TimerMode.TIME_CAP, TimerMode.REST -> {
                    // No-op for non-round / single-round modes
                }
            }
            return
        }

        when (config.mode) {
            TimerMode.EMOM -> {
                if (current.currentRound <= 1) return
                val prevRound = current.currentRound - 1
                val roundSecs = config.intervalSeconds
                val remainingTotal = (config.totalRounds - prevRound + 1) * roundSecs
                _snapshot.value = current.copy(
                    phase = TimerPhase.WORK,
                    currentRound = prevRound,
                    roundSecondsRemaining = roundSecs,
                    roundSecondsElapsed = 0,
                    roundTotalSeconds = roundSecs,
                    totalSecondsRemaining = remainingTotal
                )
            }
            TimerMode.TABATA -> {
                if (current.phase == TimerPhase.REST) {
                    // In REST: revert to WORK of the same round
                    val remainingTotal = (config.totalRounds - current.currentRound + 1) * (config.workSeconds + config.restSeconds)
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        roundSecondsRemaining = config.workSeconds,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = config.workSeconds,
                        totalSecondsRemaining = remainingTotal
                    )
                } else {
                    // In WORK: decrement to WORK of previous round if > 1
                    if (current.currentRound <= 1) return
                    val prevRound = current.currentRound - 1
                    val remainingTotal = (config.totalRounds - prevRound + 1) * (config.workSeconds + config.restSeconds)
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        currentRound = prevRound,
                        roundSecondsRemaining = config.workSeconds,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = config.workSeconds,
                        totalSecondsRemaining = remainingTotal
                    )
                }
            }
            TimerMode.DEATH_BY -> {
                if (current.currentRound <= 1) return
                val prevRound = current.currentRound - 1
                val roundSecs = 60
                val remainingTotal = (config.totalRounds - prevRound + 1) * 60
                _snapshot.value = current.copy(
                    phase = TimerPhase.WORK,
                    currentRound = prevRound,
                    roundSecondsRemaining = roundSecs,
                    roundSecondsElapsed = 0,
                    roundTotalSeconds = roundSecs,
                    targetRepsCurrentRound = prevRound,
                    totalSecondsRemaining = remainingTotal
                )
            }
            TimerMode.AMRAP, TimerMode.TIME_CAP, TimerMode.REST -> {
                // Strict no-op for single-round / non-round modes
            }
        }
    }

    private suspend fun runPrepPhase() {
        var prepLeft = config.prepCountdownSeconds
        val totalSecs = calculateInitialTotalSeconds(config)
        _snapshot.value = _snapshot.value.copy(
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
            mode = config.mode
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
        val totalSecs = calculateInitialTotalSeconds(config)
        val roundSecs = calculateInitialRoundSeconds(config)

        _snapshot.value = _snapshot.value.copy(
            phase = TimerPhase.WORK,
            isRunning = true,
            currentRound = 1,
            totalRounds = config.totalRounds,
            roundSecondsRemaining = roundSecs,
            roundSecondsElapsed = 0,
            roundTotalSeconds = roundSecs,
            totalSecondsElapsed = 0,
            totalSecondsRemaining = totalSecs,
            targetRepsCurrentRound = 1,
            workoutLabel = config.workoutLabel,
            mode = config.mode
        )

        runTimerLoop()
    }

    private suspend fun runTimerLoop() {
        while (_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.FINISHED) {
            delay(1000)
            val current = _snapshot.value

            val newRoundRemaining = current.roundSecondsRemaining - 1
            val newRoundElapsed = current.roundSecondsElapsed + 1
            val newTotalElapsed = current.totalSecondsElapsed + 1
            val newTotalRemaining = (current.totalSecondsRemaining - 1).coerceAtLeast(0)

            if (newRoundRemaining in 1..3 && config.soundEnabled) {
                playBeep(high = false)
            }
            if (newRoundRemaining in 1..3 && config.vibrationEnabled) {
                vibrateShort()
            }

            if (newRoundRemaining <= 0) {
                if (config.soundEnabled) playBeep(high = true)
                if (config.vibrationEnabled) vibrateLong()
                advanceToNextPhaseOrRound()
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

    private fun advanceToNextPhaseOrRound() {
        val current = _snapshot.value

        if (current.phase == TimerPhase.PREP) {
            timerJob?.cancel()
            val totalSecs = calculateInitialTotalSeconds(config)
            val roundSecs = calculateInitialRoundSeconds(config)
            val wasRunning = current.isRunning
            _snapshot.value = current.copy(
                phase = TimerPhase.WORK,
                isRunning = wasRunning,
                currentRound = 1,
                totalRounds = config.totalRounds,
                roundSecondsRemaining = roundSecs,
                roundSecondsElapsed = 0,
                roundTotalSeconds = roundSecs,
                totalSecondsElapsed = 0,
                totalSecondsRemaining = totalSecs,
                targetRepsCurrentRound = 1
            )
            if (wasRunning) {
                timerJob = scope.launch {
                    runTimerLoop()
                }
            }
            return
        }

        when (config.mode) {
            TimerMode.TABATA -> {
                if (current.phase == TimerPhase.WORK) {
                    // Transition WORK -> REST
                    val remainingTotal = config.restSeconds + (config.totalRounds - current.currentRound) * (config.workSeconds + config.restSeconds)
                    _snapshot.value = current.copy(
                        phase = TimerPhase.REST,
                        roundSecondsRemaining = config.restSeconds,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = config.restSeconds,
                        totalSecondsRemaining = remainingTotal
                    )
                } else {
                    // Transition REST -> WORK (Next Round)
                    if (current.currentRound >= config.totalRounds) {
                        finishTimer()
                    } else {
                        val nextRound = current.currentRound + 1
                        val remainingTotal = (config.totalRounds - nextRound + 1) * (config.workSeconds + config.restSeconds)
                        _snapshot.value = current.copy(
                            phase = TimerPhase.WORK,
                            currentRound = nextRound,
                            roundSecondsRemaining = config.workSeconds,
                            roundSecondsElapsed = 0,
                            roundTotalSeconds = config.workSeconds,
                            totalSecondsRemaining = remainingTotal
                        )
                    }
                }
            }
            TimerMode.EMOM -> {
                if (current.currentRound >= config.totalRounds) {
                    finishTimer()
                } else {
                    val nextRound = current.currentRound + 1
                    val roundSecs = config.intervalSeconds
                    val remainingTotal = (config.totalRounds - nextRound + 1) * roundSecs
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        currentRound = nextRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        totalSecondsRemaining = remainingTotal
                    )
                }
            }
            TimerMode.DEATH_BY -> {
                if (current.currentRound >= config.totalRounds) {
                    finishTimer()
                } else {
                    val nextRound = current.currentRound + 1
                    val roundSecs = 60 // Death By is 1 minute per round
                    val remainingTotal = (config.totalRounds - nextRound + 1) * 60
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        currentRound = nextRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        targetRepsCurrentRound = nextRound,
                        totalSecondsRemaining = remainingTotal
                    )
                }
            }
            TimerMode.AMRAP, TimerMode.TIME_CAP, TimerMode.REST -> {
                finishTimer()
            }
        }
    }

    private fun finishTimer() {
        timerJob?.cancel()
        _snapshot.value = _snapshot.value.copy(
            phase = TimerPhase.FINISHED,
            isRunning = false,
            roundSecondsRemaining = 0,
            totalSecondsRemaining = 0
        )
        if (config.soundEnabled) {
            playBeep(high = true)
        }
        if (config.vibrationEnabled) {
            vibrateLong()
        }
    }

    private fun calculateInitialTotalSeconds(cfg: WorkoutTimerConfig): Int = when (cfg.mode) {
        TimerMode.EMOM -> cfg.intervalSeconds * cfg.totalRounds
        TimerMode.AMRAP, TimerMode.TIME_CAP -> cfg.targetMinutes * 60
        TimerMode.DEATH_BY -> cfg.totalRounds * 60
        TimerMode.TABATA -> (cfg.workSeconds + cfg.restSeconds) * cfg.totalRounds
        TimerMode.REST -> cfg.restSeconds
    }

    private fun calculateInitialRoundSeconds(cfg: WorkoutTimerConfig): Int = when (cfg.mode) {
        TimerMode.EMOM -> cfg.intervalSeconds
        TimerMode.AMRAP, TimerMode.TIME_CAP -> cfg.targetMinutes * 60
        TimerMode.DEATH_BY -> 60
        TimerMode.TABATA -> cfg.workSeconds
        TimerMode.REST -> cfg.restSeconds
    }

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
    }
}
