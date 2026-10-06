package com.popuchoco.puricaremini

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BackgroundConnectionService : Service() {
    private val ble get() = (application as PuriCareApplication).ble

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PuriCare 背景連線", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = buildNotification("正在準備連線")
        val started = runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
        if (!started) {
            FeaturePreferences(this).setBackgroundConnection(false)
            ble.setBackgroundConnectionEnabled(false)
            stopSelf()
            return
        }
        ble.backgroundStatusListener = { status, message ->
            val text = backgroundNotificationText(status, message)
            runCatching {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        ble.setBackgroundConnectionEnabled(true)
        if (ble.state.connected) {
            ble.backgroundStatusListener?.invoke(
                BackgroundConnectionStatus.CONNECTED,
                "已連線至 ${ble.state.deviceName ?: "PuriCare Mini"}",
            )
        } else {
            ble.resumeSavedConnection()
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("PuriCare Mini 背景連線")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    override fun onDestroy() {
        ble.backgroundStatusListener = null
        ble.setBackgroundConnectionEnabled(false)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "puricare_background_connection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BackgroundConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundConnectionService::class.java))
        }
    }
}
