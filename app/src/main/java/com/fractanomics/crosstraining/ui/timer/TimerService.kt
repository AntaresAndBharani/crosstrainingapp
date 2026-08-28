package com.fractanomics.crosstraining.ui.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fractanomics.crosstraining.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service that displays an interactive MediaStyle notification for the active workout timer.
 * Integrates with [TimerTeardownController] for graceful termination on stop, reset, or finish.
 */
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "crosstraining_timer_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.fractanomics.crosstraining.action.TIMER_START"
        const val ACTION_PAUSE = "com.fractanomics.crosstraining.action.TIMER_PAUSE"
        const val ACTION_NEXT = "com.fractanomics.crosstraining.action.TIMER_NEXT"
        const val ACTION_STOP = "com.fractanomics.crosstraining.action.TIMER_STOP"
        const val ACTION_RESET = "com.fractanomics.crosstraining.action.TIMER_RESET"

        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val DESTINATION_TIMER = "timer"

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observerJob: Job? = null

    private lateinit var timerEngine: TimerEngine
    private lateinit var notificationManager: NotificationManagerCompat
    internal lateinit var teardownController: TimerTeardownController

    override fun onCreate() {
        super.onCreate()
        timerEngine = TimerEngineProvider.get(this)
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "TimerServiceMediaSession").apply {
            isActive = true
        }

        teardownController = TimerTeardownController(
            onStopForeground = { removeNotification ->
                ServiceCompat.stopForeground(
                    this,
                    if (removeNotification) ServiceCompat.STOP_FOREGROUND_REMOVE
                    else ServiceCompat.STOP_FOREGROUND_DETACH
                )
            },
            onDismissNotification = {
                notificationManager.cancel(NOTIFICATION_ID)
            },
            onReleaseMediaSession = {
                mediaSession?.let {
                    if (it.isActive) it.isActive = false
                    it.release()
                }
                mediaSession = null
            },
            onStopService = {
                stopSelf()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                teardownController.onServiceStarted()
                val notification = buildNotification(timerEngine.snapshot.value)
                startForeground(NOTIFICATION_ID, notification)
                timerEngine.start()
                startObservingTimer()
            }
            ACTION_PAUSE -> {
                timerEngine.pause()
            }
            ACTION_NEXT -> {
                timerEngine.skipRound()
            }
            ACTION_STOP -> {
                teardownController.handleStopAction(timerEngine)
            }
            ACTION_RESET -> {
                timerEngine.reset()
                teardownController.performGracefulTeardown()
            }
            else -> {
                if (!teardownController.isServiceActive) {
                    teardownController.onServiceStarted()
                    val notification = buildNotification(timerEngine.snapshot.value)
                    startForeground(NOTIFICATION_ID, notification)
                    startObservingTimer()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startObservingTimer() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            timerEngine.snapshot.collectLatest { snapshot ->
                val terminated = teardownController.onSnapshotUpdated(snapshot)
                if (!terminated && teardownController.isServiceActive) {
                    val notification = buildNotification(snapshot)
                    try {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (_: SecurityException) {
                        // Permission might not be granted in edge cases
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live workout timer status and controls"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(snapshot: TimerSnapshot): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, DESTINATION_TIMER)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (snapshot.isRunning) {
            val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            ).build()
        } else {
            val playIntent = Intent(this, TimerService::class.java).apply { action = ACTION_START }
            val playPendingIntent = PendingIntent.getService(
                this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            ).build()
        }

        val nextIntent = Intent(this, TimerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            nextPendingIntent
        ).build()

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        ).build()

        val content = TimerNotificationFormatter.createNotificationContent(snapshot)

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)

        mediaSession?.sessionToken?.let {
            style.setMediaSession(it)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(content.isRunning)
            .setOnlyAlertOnce(content.onlyAlertOnce)
            .setStyle(style)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .build()
    }

    private fun formatTime(seconds: Int): String = TimerNotificationFormatter.formatTime(seconds)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        teardownController.performGracefulTeardown()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
