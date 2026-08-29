package com.fractanomics.crosstraining.ui.timer

/**
 * Specification for an individual notification action button.
 */
data class NotificationActionSpec(
    val title: String,
    val iconRes: Int,
    val action: String,
    val requestCode: Int
)

/**
 * Encapsulates all data required to render the MediaStyle workout timer notification.
 */
data class TimerNotificationSpec(
    val title: String,
    val contentText: String,
    val isOngoing: Boolean,
    val playPauseAction: NotificationActionSpec,
    val nextAction: NotificationActionSpec,
    val stopAction: NotificationActionSpec,
    val compactActionIndices: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TimerNotificationSpec
        if (title != other.title) return false
        if (contentText != other.contentText) return false
        if (isOngoing != other.isOngoing) return false
        if (playPauseAction != other.playPauseAction) return false
        if (nextAction != other.nextAction) return false
        if (stopAction != other.stopAction) return false
        if (!compactActionIndices.contentEquals(other.compactActionIndices)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + contentText.hashCode()
        result = 31 * result + isOngoing.hashCode()
        result = 31 * result + playPauseAction.hashCode()
        result = 31 * result + nextAction.hashCode()
        result = 31 * result + stopAction.hashCode()
        result = 31 * result + compactActionIndices.contentHashCode()
        return result
    }
}

/**
 * Builds a [TimerNotificationSpec] representing the visual notification state from a given [TimerSnapshot].
 */
fun createTimerNotificationSpec(snapshot: TimerSnapshot): TimerNotificationSpec {
    val playPauseAction = if (snapshot.isRunning) {
        NotificationActionSpec(
            title = "Pause",
            iconRes = android.R.drawable.ic_media_pause,
            action = TimerService.ACTION_PAUSE,
            requestCode = 1
        )
    } else {
        NotificationActionSpec(
            title = "Play",
            iconRes = android.R.drawable.ic_media_play,
            action = TimerService.ACTION_START,
            requestCode = 2
        )
    }

    val nextAction = NotificationActionSpec(
        title = "Next",
        iconRes = android.R.drawable.ic_media_next,
        action = TimerService.ACTION_NEXT,
        requestCode = 3
    )

    val stopAction = NotificationActionSpec(
        title = "Stop",
        iconRes = android.R.drawable.ic_menu_close_clear_cancel,
        action = TimerService.ACTION_STOP,
        requestCode = 4
    )

    val roundInfo = "Round ${snapshot.currentRound}/${snapshot.totalRounds}"
    val title = "${snapshot.phase.label} - $roundInfo"
    val remainingFormatted = formatTimerNotificationTime(snapshot.roundSecondsRemaining)
    val totalRemainingFormatted = formatTimerNotificationTime(snapshot.totalSecondsRemaining)
    val contentText = "Remaining: $remainingFormatted | Total Left: $totalRemainingFormatted"

    return TimerNotificationSpec(
        title = title,
        contentText = contentText,
        isOngoing = snapshot.isRunning,
        playPauseAction = playPauseAction,
        nextAction = nextAction,
        stopAction = stopAction,
        compactActionIndices = intArrayOf(0, 1)
    )
}

fun formatTimerNotificationTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
