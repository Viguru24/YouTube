package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class MediaPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            // Acquire WakeLock to keep CPU active during background playback
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouTubePlayer::BackgroundPlaybackWakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L) // 10 hours max safety timeout
            }
        } catch (e: Exception) { }

        try {
            // Acquire WifiLock to keep network streaming alive when screen is turned off
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YouTubePlayer::BackgroundPlaybackWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "YouTube Video"
        val channel = intent?.getStringExtra(EXTRA_CHANNEL) ?: "Playing in Background"

        val notification = buildNotification(title, channel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(title: String, channel: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            this.action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(channel)
            .setSubText("Continuous Background Audio Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Playback", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps YouTube audio playing continuously in background without dying"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 9901
        const val ACTION_START = "com.example.action.START_MEDIA_PLAYBACK"
        const val ACTION_STOP = "com.example.action.STOP_MEDIA_PLAYBACK"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"

        fun start(context: Context, title: String = "YouTube Video", channel: String = "Playing in Background") {
            try {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    this.action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_CHANNEL, channel)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) { }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    this.action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) { }
        }
    }
}
