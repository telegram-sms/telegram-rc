package com.qwe7002.telegram_rc.static_class

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.qwe7002.telegram_rc.BatteryService
import com.qwe7002.telegram_rc.BeaconReceiverService
import com.qwe7002.telegram_rc.NotifyListenerService
import com.qwe7002.telegram_rc.ChatService

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
                ComponentName(context, NotifyListenerService::class.java)
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
            val chatLongPollingService = Intent(context, ChatService::class.java)
            context.startForegroundService(chatLongPollingService)
        }
    }

    @JvmStatic
    fun startBeaconService(context: Context) {
        if (hasLocationPermissions(context)) {
            val beaconService = Intent(context, BeaconReceiverService::class.java)
            context.startForegroundService(beaconService)
        }
    }

    private fun hasLocationPermissions(context: Context): Boolean {
        val fineLocationPermission =
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission =
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val foregroundServiceLocationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            } else {
                PackageManager.PERMISSION_GRANTED
            }
        return fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                foregroundServiceLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isNotifyListener(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }
}
