package com.fractanomics.crosstraining.ui.timer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerEngine(private val context: Context? = null) {

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

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private var config = WorkoutTimerConfig()
    private val _snapshot = MutableStateFlow(TimerSnapshot())
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    fun configure(newConfig: WorkoutTimerConfig) {
        if (!_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.PREP) {
            config = newConfig
            reset()
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
            targetRepsCurrentRound = 1
        )
    }

    fun skipRound() {
        if (!_snapshot.value.isRunning && _snapshot.value.phase != TimerPhase.WORK && _snapshot.value.phase != TimerPhase.REST) return
        advanceToNextPhaseOrRound()
    }

    private suspend fun runPrepPhase() {
        var prepLeft = config.prepCountdownSeconds
        _snapshot.value = _snapshot.value.copy(
            phase = TimerPhase.PREP,
            isRunning = true,
            roundSecondsRemaining = prepLeft,
            roundSecondsElapsed = 0,
            roundTotalSeconds = config.prepCountdownSeconds
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
            targetRepsCurrentRound = 1
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

        when (config.mode) {
            TimerMode.TABATA -> {
                if (current.phase == TimerPhase.WORK) {
                    // Transition WORK -> REST
                    _snapshot.value = current.copy(
                        phase = TimerPhase.REST,
                        roundSecondsRemaining = config.restSeconds,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = config.restSeconds
                    )
                } else {
                    // Transition REST -> WORK (Next Round)
                    if (current.currentRound >= config.totalRounds) {
                        finishTimer()
                    } else {
                        val nextRound = current.currentRound + 1
                        _snapshot.value = current.copy(
                            phase = TimerPhase.WORK,
                            currentRound = nextRound,
                            roundSecondsRemaining = config.workSeconds,
                            roundSecondsElapsed = 0,
                            roundTotalSeconds = config.workSeconds
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
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        currentRound = nextRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs
                    )
                }
            }
            TimerMode.DEATH_BY -> {
                if (current.currentRound >= config.totalRounds) {
                    finishTimer()
                } else {
                    val nextRound = current.currentRound + 1
                    val roundSecs = 60 // Death By is 1 minute per round
                    _snapshot.value = current.copy(
                        phase = TimerPhase.WORK,
                        currentRound = nextRound,
                        roundSecondsRemaining = roundSecs,
                        roundSecondsElapsed = 0,
                        roundTotalSeconds = roundSecs,
                        targetRepsCurrentRound = nextRound
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

    fun release() {
        timerJob?.cancel()
        toneGenerator?.release()
    }
}
