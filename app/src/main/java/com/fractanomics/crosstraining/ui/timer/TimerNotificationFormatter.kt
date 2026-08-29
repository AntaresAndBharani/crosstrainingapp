package com.fractanomics.crosstraining.ui.timer

/**
 * Formats timer state into display strings for notification titles, body text, and media descriptions.
 */
object TimerNotificationFormatter {

    /**
     * Formats round progression string, e.g. "Round 3/8".
     */
    fun formatRoundInfo(currentRound: Int, totalRounds: Int): String =
        "Round $currentRound/$totalRounds"

    /**
     * Formats notification title combining phase label and round information,
     * e.g. "Work - Round 3/8", "Rest - Round 2/5", "Get Ready! - Round 1/10".
     */
    fun formatTitle(phase: TimerPhase, currentRound: Int, totalRounds: Int): String =
        "${phase.label} - ${formatRoundInfo(currentRound, totalRounds)}"

    /**
     * Formats seconds into MM:SS format, clamping negative values to 00:00.
     */
    fun formatTime(seconds: Int): String {
        val clamped = if (seconds < 0) 0 else seconds
        val m = clamped / 60
        val s = clamped % 60
        return "%02d:%02d".format(m, s)
    }

    /**
     * Formats the content text for the notification body, e.g.
     * "Remaining: 00:20 | Total Left: 03:00".
     */
    fun formatContentText(roundSecondsRemaining: Int, totalSecondsRemaining: Int): String {
        val remainingFormatted = formatTime(roundSecondsRemaining)
        val totalRemainingFormatted = formatTime(totalSecondsRemaining)
        return "Remaining: $remainingFormatted | Total Left: $totalRemainingFormatted"
    }

    /**
     * Creates a structured data representation of the notification content derived from a [TimerSnapshot].
     */
    fun createNotificationContent(snapshot: TimerSnapshot): TimerNotificationContent {
        return TimerNotificationContent(
            title = formatTitle(snapshot.phase, snapshot.currentRound, snapshot.totalRounds),
            contentText = formatContentText(snapshot.roundSecondsRemaining, snapshot.totalSecondsRemaining),
            phase = snapshot.phase,
            currentRound = snapshot.currentRound,
            totalRounds = snapshot.totalRounds,
            roundSecondsRemaining = snapshot.roundSecondsRemaining,
            totalSecondsRemaining = snapshot.totalSecondsRemaining,
            isRunning = snapshot.isRunning,
            onlyAlertOnce = true
        )
    }
}

/**
 * Data representation of the notification content for verification and testing.
 */
data class TimerNotificationContent(
    val title: String,
    val contentText: String,
    val phase: TimerPhase,
    val currentRound: Int,
    val totalRounds: Int,
    val roundSecondsRemaining: Int,
    val totalSecondsRemaining: Int,
    val isRunning: Boolean,
    val onlyAlertOnce: Boolean = true
)
