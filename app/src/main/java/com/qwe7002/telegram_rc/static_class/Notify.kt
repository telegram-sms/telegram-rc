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
        val notification = Notification.Builder(context, subject)
            .setContentTitle(subject)
            .setContentText(message)
        notification.setContentIntent(pendingIntent)
        notification.setSmallIcon(android.R.drawable.stat_sys_warning)
        notification.setAutoCancel(true)
        notification.setDefaults(Notification.DEFAULT_ALL)
        notification.setPriority(Notification.PRIORITY_HIGH)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(subject, subject, android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }else {
            notification.setVibrate(longArrayOf(0, 1000))
        }
        val notify = notification.build()
        notificationManager.notify(subject.hashCode(), notify)


    }
}
