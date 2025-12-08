package com.qwe7002.telegram_rc.static_class

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.qwe7002.telegram_rc.MainActivity

object Notify {
    const val BATTERY: Int = 1
    const val CHAT_COMMAND: Int = 2
    const val BEACON_SERVICE: Int = 3
    @JvmStatic
    fun sendMessage(context: Context, subject: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // 創建 NotificationChannel（Android O+ 必需）
        val channel = android.app.NotificationChannel(
            subject,
            subject,
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000)
        }
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, subject)
            .setContentTitle(subject)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(subject.hashCode(), notification)
    }
}
