package com.qwe7002.telegram_rc.static_class

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.qwe7002.telegram_rc.BatteryService
import com.qwe7002.telegram_rc.BeaconReceiverService
import com.qwe7002.telegram_rc.NotificationListenerService
import com.qwe7002.telegram_rc.chat_command_service

object ServiceManage {
    @JvmStatic
    fun stopAllService(context: Context) {
        val intent = Intent(Const.BROADCAST_STOP_SERVICE)
        context.sendBroadcast(intent)
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Log.e("Service", "stopAllService: ", e)
        }
    }

    @JvmStatic
    fun startService(context: Context, batterySwitch: Boolean, chatCommandSwitch: Boolean) {
        if (isNotifyListener(context)) {
            val thisComponentName =
                ComponentName(context, NotificationListenerService::class.java)
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                thisComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                thisComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (batterySwitch) {
            val batteryService = Intent(context, BatteryService::class.java)
            context.startForegroundService(batteryService)
        }
        if (chatCommandSwitch) {
            val chatLongPollingService = Intent(context, chat_command_service::class.java)
            context.startForegroundService(chatLongPollingService)
        }
    }

    @JvmStatic
    fun startBeaconService(context: Context) {
        val beaconService = Intent(context, BeaconReceiverService::class.java)
        context.startForegroundService(beaconService)
    }

    @JvmStatic
    fun isNotifyListener(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }
}
