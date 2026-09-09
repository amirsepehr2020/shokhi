package com.amirsepehr.shokhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL

class StatusService : Service() {
    companion object {
        const val ACTION_START = "com.amirsepehr.shokhi.START_STATUS"
        const val ACTION_STOP = "com.amirsepehr.shokhi.STOP_STATUS"
        private const val CHANNEL_ID = "status_sharing"
        private const val NOTIFICATION_ID = 7001
        private const val INTERVAL_MS = 15_000L
        private const val API_URL = "https://shokhi-site.sepehr2sodoury.workers.dev/api/status"
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val task = object : Runnable {
        override fun run() {
            if (!isSharing()) {
                stopSelf()
                return
            }
            sendStatus()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !isSharing()) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(task)
        sendStatus()
        handler.postDelayed(task, INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(task)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isSharing() = getSharedPreferences("shokhi", MODE_PRIVATE).getBoolean("sharing", false)

    private fun sendStatus() {
        val prefs = getSharedPreferences("shokhi", MODE_PRIVATE)
        val user = prefs.getString("user", null) ?: return
        val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        Thread {
            try {
                val connection = URL(API_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = "{\"user\":\"$user\",\"screenOn\":$screenOn}"
                connection.outputStream.use { it.write(payload.toByteArray()) }
                connection.inputStream.use { it.readBytes() }
                connection.disconnect()
            } catch (_: Exception) {
                // Offline is expected sometimes; the next heartbeat retries automatically.
            }
        }.start()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Status sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows when Shokhi status sharing is active."
                }
            )
        }
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("ماشین حساب — اشتراک وضعیت فعال است")
            .setContentText("وضعیت دستگاه هر ۱۵ ثانیه همگام می‌شود. برای توقف، Sharing را خاموش کنید.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("shokhi", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("sharing", false)) return
        val serviceIntent = Intent(context, StatusService::class.java).setAction(StatusService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}
