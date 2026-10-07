package com.popuchoco.puricaremini

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class FilterReminderNotifier(private val context: Context) {
    private val preferences = FeaturePreferences(context)

    fun evaluate(snapshot: AirSnapshot) {
        val threshold = preferences.filterReminderThreshold ?: return
        val life = snapshot.filterLife() ?: return
        val sentThreshold = preferences.filterReminderSentThreshold()
        if (FilterReminderPolicy.shouldReset(life.percent, threshold, sentThreshold)) {
            preferences.setFilterReminderSentThreshold(null)
            return
        }
        if (!FilterReminderPolicy.shouldNotify(life.percent, threshold, sentThreshold)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "濾網更換提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PuriCare Mini 濾網提醒")
            .setContentText("濾網壽命剩餘 ${life.percent}%，建議準備更換濾網。")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        preferences.setFilterReminderSentThreshold(threshold)
    }

    private companion object {
        const val CHANNEL_ID = "filter_reminder"
        const val NOTIFICATION_ID = 2002
    }
}
