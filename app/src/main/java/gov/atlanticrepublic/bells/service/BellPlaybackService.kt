package gov.atlanticrepublic.bells.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import gov.atlanticrepublic.bells.R
import gov.atlanticrepublic.bells.config.AppPreferences
import gov.atlanticrepublic.bells.model.BellSchedule
import gov.atlanticrepublic.bells.model.WatchSystem
import gov.atlanticrepublic.bells.scheduling.BellAlarmManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime

class BellPlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "ships_bell_service"
        private const val NOTIFICATION_ID = 1
        private const val TIMEOUT_MS = 30_000L
        const val EXTRA_FORCE_PLAY = "force_play"
        const val EXTRA_AUDIO_NAME = "audio_name"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var bellVolume: Float = 0.5f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground within 5 seconds on Android 14
        startForeground(NOTIFICATION_ID, buildNotification())

        val forcePlay = intent?.getBooleanExtra(EXTRA_FORCE_PLAY, false) ?: false
        val forcedAudioName = intent?.getStringExtra(EXTRA_AUDIO_NAME)

        // Schedule the next alarm immediately so it's never lost
        BellAlarmManager.scheduleNext(this)

        // Read preferences synchronously (brief, safe in service start)
        val prefs = AppPreferences(this)
        val bellsEnabled: Boolean
        val quietEnabled: Boolean
        val quietStart: LocalTime
        val quietEnd: LocalTime
        val watchSystem: WatchSystem

        runBlocking {
            bellsEnabled = prefs.bellsEnabled.first()
            bellVolume = prefs.bellVolume.first()
            quietEnabled = prefs.quietEnabled.first()
            quietStart = prefs.quietStart.first()
            quietEnd = prefs.quietEnd.first()
            watchSystem = prefs.watchSystem.first()
        }

        if (forcePlay && forcedAudioName != null) {
            // Bypass all checks — direct playback for testing
            playBell(forcedAudioName)
            return START_NOT_STICKY
        }

        if (!bellsEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        val now = LocalDateTime.now()

        if (quietEnabled && prefs.isInQuietHours(now.toLocalTime(), quietStart, quietEnd)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val audioName = BellSchedule.getAudioResourceName(now, watchSystem)
        if (audioName == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        playBell(audioName)
        return START_NOT_STICKY
    }

    private fun playBell(audioName: String) {
        val resId = resources.getIdentifier(audioName, "raw", packageName)
        if (resId == 0) {
            stopSelf()
            return
        }

        try {
            mediaPlayer = MediaPlayer.create(this, resId).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener {
                    cleanup()
                    stopSelf()
                }
                setOnErrorListener { _, _, _ ->
                    cleanup()
                    stopSelf()
                    true
                }
                setVolume(bellVolume, bellVolume)
                start()
            }

            // Safety timeout in case completion never fires
            handler.postDelayed({
                cleanup()
                stopSelf()
            }, TIMEOUT_MS)
        } catch (e: Exception) {
            cleanup()
            stopSelf()
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) { }
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ships Bell",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Bell playback service notification"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ships Bell")
                .setContentText("Ringing the bell...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Ships Bell")
                .setContentText("Ringing the bell...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()
        }
    }
}
